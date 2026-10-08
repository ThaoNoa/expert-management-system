package com.npcore.ems.expert;

import com.npcore.ems.expert.ExpertDtos.ExpertDetail;
import com.npcore.ems.expert.ExpertDtos.ExpertRequest;
import com.npcore.ems.identity.User;
import com.npcore.ems.identity.UserRepository;
import com.npcore.ems.identity.UserService;
import com.npcore.ems.identity.Department;
import com.npcore.ems.identity.DepartmentRepository;
import com.npcore.ems.shared.audit.AuditService;
import com.npcore.ems.shared.importing.TabularFileParser;
import com.npcore.ems.shared.security.SecurityUtils;
import com.npcore.ems.shared.web.ApiException;
import com.npcore.ems.shared.web.ImportResult;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Chuyển hồ sơ chuyên gia cũ vào hệ thống (S6). Dòng hợp lệ được nhập, dòng lỗi được báo kèm số dòng.
 * Mọi kiểm tra chạy TRƯỚC khi ghi để một dòng lỗi không làm hỏng transaction.
 */
@Service
@RequiredArgsConstructor
public class ExpertImportService {

    public static final List<String> COLUMNS = List.of("expert_code", "full_name", "date_of_birth", "gender",
            "id_number", "address", "phone", "email", "expert_type", "employment_type", "department_code",
            "position", "joined_date", "username");

    /** Kết quả import hồ sơ: như ImportResult + tài khoản đã tạo (kèm mật khẩu tạm) và cảnh báo không chặn dòng. */
    public record ExpertImportResult(int total, int imported, int skipped, List<ImportResult.RowError> errors,
                                     List<ImportResult.RowError> warnings, List<CreatedAccount> accounts) {}

    public record CreatedAccount(int row, String expertCode, String fullName, String username, String email,
                                 String tempPassword) {}

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern USERNAME = Pattern.compile("[a-z0-9._-]{3,100}");

    private static final DateTimeFormatter VN_DATE = DateTimeFormatter.ofPattern("d/M/uuuu");

    private final TabularFileParser parser;
    private final ExpertService experts;
    private final ExpertRepository expertRepository;
    private final DepartmentRepository departments;
    private final Validator validator;
    private final AuditService audit;
    private final UserService userService;
    private final UserRepository users;

    public String templateCsv() {
        return "﻿" + String.join(",", COLUMNS) + "\n"
                + ",Nguyễn Văn A,1980-05-20,MALE,001080000001,Hà Nội,0912345678,a@example.com,AUDITOR,FULLTIME,QA,Chuyên gia đánh giá,2015-03-01,\n"
                + "PT-015,Trần Thị B,12/08/1985,FEMALE,,Hải Phòng,,b@example.com,CGKT,Parttime,,,,tran.b\n";
    }

    @Transactional
    public ImportResult importFile(MultipartFile file) {
        ExpertImportResult r = importFile(file, false);
        return new ImportResult(r.total(), r.imported(), r.skipped(), r.errors());
    }

    /**
     * createAccounts = true: mỗi chuyên gia nhập thành công có email sẽ được tạo tài khoản vai trò Chuyên gia
     * (tên đăng nhập lấy từ cột username, trống thì từ phần trước @ của email), mật khẩu tạm, bắt đổi lần đầu.
     * Lỗi tài khoản (thiếu email, trùng...) chỉ là cảnh báo – hồ sơ vẫn được nhập, gắn tài khoản sau bằng tay.
     */
    @Transactional
    public ExpertImportResult importFile(MultipartFile file, boolean createAccounts) {
        SecurityUtils.require("EXPERT_CREATE");
        List<Map<String, String>> rows = parser.parse(file);
        if (!rows.isEmpty() && rows.stream().filter(r -> !r.isEmpty()).findFirst()
                .map(r -> !r.containsKey("full_name")).orElse(false)) {
            throw ApiException.badRequest("Thiếu cột full_name. Các cột: " + String.join(", ", COLUMNS));
        }
        Map<String, Department> deptByCode = departments.findAll().stream()
                .collect(Collectors.toMap(d -> d.getCode().toUpperCase(Locale.ROOT), Function.identity(), (a, b) -> a));
        ImportResult.Builder result = new ImportResult.Builder();
        Set<String> codesInFile = new HashSet<>();
        Set<String> usernamesInFile = new HashSet<>();
        Set<String> emailsInFile = new HashSet<>();
        List<ImportResult.RowError> warnings = new ArrayList<>();
        List<CreatedAccount> accounts = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            int rowNo = i + 2;
            if (row.isEmpty()) continue;
            result.row();
            try {
                String code = blank(row.get("expert_code"));
                if (code != null) {
                    code = code.toUpperCase(Locale.ROOT);
                    // cột expert_code VARCHAR(30): kiểm tra trước để lỗi DB không làm hỏng cả transaction import
                    if (code.length() > 30) throw new RowException("Mã chuyên gia dài quá 30 ký tự: " + code);
                    if (!codesInFile.add(code)) throw new RowException("Mã chuyên gia bị lặp trong file: " + code);
                    if (expertRepository.existsByCode(code)) throw new RowException("Mã chuyên gia đã tồn tại: " + code);
                }
                String deptCode = blank(row.get("department_code"));
                Department dept = deptCode == null ? null : deptByCode.get(deptCode.toUpperCase(Locale.ROOT));
                if (deptCode != null && dept == null) throw new RowException("Không có phòng ban mã " + deptCode);
                ExpertRequest req = new ExpertRequest(
                        blank(row.get("full_name")), date(row.get("date_of_birth"), "date_of_birth"),
                        gender(row.get("gender")), blank(row.get("id_number")), blank(row.get("address")),
                        blank(row.get("phone")), blank(row.get("email")), expertType(row.get("expert_type")),
                        employmentType(row.get("employment_type")), dept == null ? null : dept.getId(),
                        blank(row.get("position")), date(row.get("joined_date"), "joined_date"), null, null, null);
                Set<ConstraintViolation<ExpertRequest>> violations = validator.validate(req);
                if (!violations.isEmpty()) {
                    throw new RowException(violations.stream().map(v -> v.getPropertyPath() + ": " + v.getMessage())
                            .sorted().collect(Collectors.joining("; ")));
                }
                // kiểm tra tài khoản TRƯỚC khi ghi để lỗi không làm hỏng transaction
                String username = null;
                if (createAccounts) {
                    try {
                        username = planUsername(row, req.email(), usernamesInFile, emailsInFile);
                    } catch (RowException w) {
                        warnings.add(new ImportResult.RowError(rowNo, "Chưa tạo tài khoản: " + w.getMessage()));
                    }
                }
                ExpertDetail created = experts.create(req, code);
                result.imported();
                if (username != null) {
                    String temp = tempPassword();
                    User u = userService.createExpertAccount(username, req.email(), req.fullName(), req.departmentId(),
                            req.position(), temp);
                    expertRepository.findById(created.id()).ifPresent(e -> e.setUserId(u.getId()));
                    accounts.add(new CreatedAccount(rowNo, created.expertCode(), created.fullName(), username,
                            req.email(), temp));
                }
            } catch (RowException e) {
                result.error(rowNo, e.getMessage());
            } catch (ApiException e) {
                result.error(rowNo, e.getMessage());
            }
        }
        ImportResult r = result.build();
        audit.record("IMPORT", "EXPERT", null, null, Map.of("file", String.valueOf(file.getOriginalFilename()),
                "total", r.total(), "imported", r.imported(), "errors", r.errors().size(),
                "accounts", accounts.size()), null);
        return new ExpertImportResult(r.total(), r.imported(), r.skipped(), r.errors(), List.copyOf(warnings),
                List.copyOf(accounts));
    }

    /** Tên đăng nhập cho dòng này; ném RowException (= cảnh báo) nếu không tạo được tài khoản. */
    private String planUsername(Map<String, String> row, String email, Set<String> usernamesInFile,
                                Set<String> emailsInFile) {
        if (email == null) throw new RowException("thiếu email (tài khoản bắt buộc có email)");
        String emailKey = email.toLowerCase(Locale.ROOT);
        if (!emailsInFile.add(emailKey)) throw new RowException("email " + email + " bị lặp trong file");
        if (users.emailTaken(email, null)) throw new RowException("email " + email + " đã có tài khoản – gắn tay trong hồ sơ");
        String given = blank(row.get("username"));
        if (given != null) {
            String u = given.toLowerCase(Locale.ROOT);
            if (!USERNAME.matcher(u).matches()) {
                throw new RowException("tên đăng nhập '" + given + "' không hợp lệ (3–100 ký tự: chữ không dấu, số, . _ -)");
            }
            if (!usernamesInFile.add(u)) throw new RowException("tên đăng nhập " + u + " bị lặp trong file");
            if (users.usernameTaken(u)) throw new RowException("tên đăng nhập " + u + " đã tồn tại");
            return u;
        }
        String base = ExpertService.stripAccents(email.substring(0, email.indexOf('@')))
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "");
        if (base.length() < 3) base = base + "user";
        if (base.length() > 90) base = base.substring(0, 90);
        String candidate = base;
        for (int n = 2; usernamesInFile.contains(candidate) || users.usernameTaken(candidate); n++) {
            candidate = base + n;
        }
        usernamesInFile.add(candidate);
        return candidate;
    }

    /** Mật khẩu tạm 10 ký tự, đủ chữ hoa, chữ thường, số, ký tự đặc biệt; bỏ ký tự dễ nhầm (0/O, 1/l/I). */
    static String tempPassword() {
        String upper = "ABCDEFGHJKLMNPQRSTUVWXYZ", lower = "abcdefghijkmnpqrstuvwxyz", digit = "23456789",
                special = "@#$%!?";
        String all = upper + lower + digit + special;
        List<Character> chars = new ArrayList<>();
        for (String set : List.of(upper, lower, digit, special)) chars.add(set.charAt(RANDOM.nextInt(set.length())));
        while (chars.size() < 10) chars.add(all.charAt(RANDOM.nextInt(all.length())));
        Collections.shuffle(chars, RANDOM);
        StringBuilder sb = new StringBuilder();
        chars.forEach(sb::append);
        return sb.toString();
    }

    private static final class RowException extends RuntimeException {
        RowException(String m) { super(m); }
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    static LocalDate date(String raw, String column) {
        String s = blank(raw);
        if (s == null) return null;
        try {
            return s.contains("/") ? LocalDate.parse(s, VN_DATE) : LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            throw new RowException(column + ": ngày không hợp lệ '" + s + "' (dùng yyyy-MM-dd hoặc dd/MM/yyyy)");
        }
    }

    static String gender(String raw) {
        String s = blank(raw);
        if (s == null) return null;
        return switch (ExpertService.stripAccents(s).toUpperCase(Locale.ROOT)) {
            case "MALE", "NAM", "M" -> "MALE";
            case "FEMALE", "NU", "F" -> "FEMALE";
            case "OTHER", "KHAC" -> "OTHER";
            default -> throw new RowException("gender không hợp lệ: " + s);
        };
    }

    static String expertType(String raw) {
        String s = blank(raw);
        if (s == null) throw new RowException("expert_type bắt buộc (AUDITOR/CGĐG, TECHNICAL_EXPERT/CGKT, BOTH)");
        return switch (ExpertService.stripAccents(s).toUpperCase(Locale.ROOT).replace(' ', '_')) {
            case "AUDITOR", "CGDG" -> "AUDITOR";
            case "TECHNICAL_EXPERT", "CGKT", "TE" -> "TECHNICAL_EXPERT";
            case "BOTH", "CA_HAI" -> "BOTH";
            default -> throw new RowException("expert_type không hợp lệ: " + s);
        };
    }

    static String employmentType(String raw) {
        String s = blank(raw);
        if (s == null) throw new RowException("employment_type bắt buộc (FULLTIME/PARTTIME)");
        return switch (s.toUpperCase(Locale.ROOT).replace("-", "").replace(" ", "")) {
            case "FULLTIME", "FT" -> "FULLTIME";
            case "PARTTIME", "PT" -> "PARTTIME";
            default -> throw new RowException("employment_type không hợp lệ: " + s);
        };
    }
}
