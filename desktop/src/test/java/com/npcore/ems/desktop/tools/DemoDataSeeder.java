package com.npcore.ems.desktop.tools;

import com.npcore.ems.desktop.api.Api;
import com.npcore.ems.desktop.api.ApiClient;
import com.npcore.ems.desktop.api.Dtos.CertificateRequest;
import com.npcore.ems.desktop.api.Dtos.Code;
import com.npcore.ems.desktop.api.Dtos.CodeSet;
import com.npcore.ems.desktop.api.Dtos.CreateUserRequest;
import com.npcore.ems.desktop.api.Dtos.EducationRequest;
import com.npcore.ems.desktop.api.Dtos.ExperienceRequest;
import com.npcore.ems.desktop.api.Dtos.ExpertDetail;
import com.npcore.ems.desktop.api.Dtos.ExpertRequest;
import com.npcore.ems.desktop.api.Dtos.LanguageRequest;
import com.npcore.ems.desktop.api.Dtos.TrainingRequest;
import com.npcore.ems.desktop.api.Dtos.UploadResult;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Nạp dữ liệu DEMO để chạy thử EMS (gọi API thật nên đi đúng quy trình: trình duyệt, phê duyệt, nhật ký...).
 *
 * <p>Chạy: mở file này trong IntelliJ → bấm ▶ cạnh {@code main}. Backend phải đang chạy.
 * Tham số (tuỳ chọn): {@code [serverUrl] [mậtKhẩuAdmin]} – mặc định http://localhost:8080 và Admin@12345.
 *
 * <p>Chỉ dùng cho môi trường thử nghiệm. Chạy lại lần 2 sẽ dừng ngay (đã có user "hang").
 */
public final class DemoDataSeeder {

    static final String PASSWORD = "Passw0rd!x";

    private final String server;
    private final Api admin;
    private Api hang;       // NV hồ sơ chuyên gia
    private Api gdcn;       // Giám đốc chứng nhận
    private Api cgtruong;   // Chuyên gia trưởng – thẩm tra
    private Api vanphong;   // Văn phòng – xác minh tài liệu

    private final Map<String, UUID> dept = new HashMap<>();
    private final Map<String, UUID> loc = new HashMap<>();
    private final Map<String, UUID> field = new HashMap<>();
    private final Map<String, UUID> industry = new HashMap<>();
    private final Map<String, UUID> standard = new HashMap<>();
    private final Map<String, UUID> code = new HashMap<>();
    private final Path tmp;
    private int docSeq;

    public static void main(String[] args) throws Exception {
        String server = args.length > 0 ? args[0] : "http://localhost:8080";
        String adminPassword = args.length > 1 ? args[1] : "Admin@12345";
        new DemoDataSeeder(server, adminPassword).run();
    }

    DemoDataSeeder(String server, String adminPassword) throws Exception {
        this.server = server;
        this.admin = login("admin", adminPassword);
        this.tmp = Files.createTempDirectory("ems-demo");
    }

    private Api login(String user, String password) {
        Api api = new Api(new ApiClient(server));
        api.login(user, password);
        return api;
    }

    void run() {
        if (!admin.users("hang", null, 0, 1).content().isEmpty()) {
            if (admin.users("cgtruong", null, 0, 1).content().isEmpty()) {   // dữ liệu demo cũ: bổ sung Chuyên gia trưởng
                dept.put("PCN", admin.departments().stream().filter(d -> "PCN".equals(d.departmentCode()))
                        .map(d -> d.id()).findFirst().orElse(null));
                user("cgtruong", "Chuyên gia trưởng", "PCN", "Chuyên gia trưởng – VICB.009", "TECHNICAL_REVIEWER");
                System.out.println("Đã thêm tài khoản cgtruong (Chuyên gia trưởng), mật khẩu " + PASSWORD + ".");
            }
            System.out.println("Đã có dữ liệu demo (user 'hang' tồn tại) – không nạp lại phần cơ bản.");
            hang = login("hang", PASSWORD);
            gdcn = login("gdcn", PASSWORD);
            cgtruong = login("cgtruong", PASSWORD);
        } else {
            step("Danh mục", this::masterData);
            step("Người dùng & vai trò", this::users);
            step("Chuyên gia", this::experts);
        }
        if (admin.competencyDefinitions(null, null, null, 0, 1).totalElements() == 0) {
            step("Định nghĩa năng lực", this::competencyDefinitions);
            step("Năng lực chuyên gia (qua đủ quy trình duyệt)", this::expertCompetencies);
        } else {
            System.out.println("Đã có định nghĩa năng lực – không nạp lại phần năng lực.");
        }
        System.out.println();
        System.out.println("XONG. Đăng nhập app để thử (mật khẩu chung: " + PASSWORD + "):");
        System.out.println("  hang      – Nhân viên hồ sơ chuyên gia (Phan Hằng): lập hồ sơ, trình phê duyệt");
        System.out.println("  cgtruong  – Chuyên gia trưởng: thẩm tra hồ sơ và năng lực");
        System.out.println("  → Xem: Năng lực → Ma trận năng lực / Định nghĩa năng lực; tab 'Năng lực' trong hồ sơ chuyên gia");
        System.out.println("  gdcn      – Giám đốc chứng nhận: phê duyệt / trả lại, dừng / mở chuyên gia");
        System.out.println("  tpcn      – Trưởng phòng chứng nhận: xem hồ sơ (chỉ đọc)");
        System.out.println("  vanphong  – Văn phòng: nhập thông tin nhân sự, tải lên / xác minh tài liệu");
        System.out.println("  an.nv     – Chuyên gia Nguyễn Văn An: chỉ thấy hồ sơ của mình, sửa liên hệ, tải tài liệu");
        System.out.println("  admin     – Quản trị hệ thống");
    }

    private static void step(String name, Runnable r) {
        System.out.print("• " + name + "… ");
        r.run();
        System.out.println("ok");
    }

    // ================================================================ danh mục

    private void masterData() {
        for (String[] d : new String[][] {{"PCN", "Phòng Chứng nhận"}, {"PKT", "Phòng Kỹ thuật"},
                {"VP", "Văn phòng"}, {"CN-HCM", "Chi nhánh TP. Hồ Chí Minh"}}) {
            dept.put(d[0], id(admin.create("departments", Map.of("departmentCode", d[0], "departmentName", d[1]))));
        }
        for (String[] l : new String[][] {{"Hà Nội", "Bắc"}, {"Hải Phòng", "Bắc"}, {"Bắc Ninh", "Bắc"},
                {"Đà Nẵng", "Trung"}, {"TP. Hồ Chí Minh", "Nam"}, {"Bình Dương", "Nam"}, {"Cần Thơ", "Nam"}}) {
            loc.put(l[0], id(admin.create("locations", Map.of("locationName", l[0], "province", l[0],
                    "country", "VN", "region", l[1]))));
        }
        for (String[] f : new String[][] {{"CK", "Cơ khí"}, {"DDT", "Điện – Điện tử"}, {"HH", "Hóa học"},
                {"CNTP", "Công nghệ thực phẩm"}, {"XD", "Xây dựng"}, {"MT", "Môi trường"},
                {"QTKD", "Quản trị kinh doanh"}, {"CNTT", "Công nghệ thông tin"}}) {
            field.put(f[0], id(admin.create("education-fields", Map.of("fieldCode", f[0], "fieldName", f[1]))));
        }
        for (String[] i : new String[][] {{"CKCT", "Cơ khí chế tạo"}, {"DDT", "Điện – điện tử"},
                {"TP", "Thực phẩm – đồ uống"}, {"XD", "Xây dựng"}, {"TM", "Thương mại – dịch vụ"},
                {"CNTT", "Công nghệ thông tin"}, {"LOG", "Vận tải – kho vận"}, {"HC", "Hóa chất"}}) {
            industry.put(i[0], id(admin.create("industries", Map.of("industryCode", i[0], "industryName", i[1]))));
        }
        for (String[] a : new String[][] {{"IA", "Đánh giá chứng nhận lần đầu"}, {"S1", "Giám sát lần 1"},
                {"S2", "Giám sát lần 2"}, {"RC", "Tái chứng nhận"}, {"SP", "Đánh giá đặc biệt"}}) {
            admin.create("activities", Map.of("activityCode", a[0], "activityName", a[1]));
        }

        // Scheme → Tiêu chuẩn → Phiên bản
        String[][] schemes = {
                {"QMS", "Hệ thống quản lý chất lượng", "ISO 9001", "ISO 9001 – Hệ thống quản lý chất lượng", "2015", "2015-09-15"},
                {"EMS", "Hệ thống quản lý môi trường", "ISO 14001", "ISO 14001 – Hệ thống quản lý môi trường", "2015", "2015-09-15"},
                {"OHSMS", "Hệ thống quản lý an toàn sức khỏe nghề nghiệp", "ISO 45001", "ISO 45001 – An toàn sức khỏe nghề nghiệp", "2018", "2018-03-12"},
                {"FSMS", "Hệ thống quản lý an toàn thực phẩm", "ISO 22000", "ISO 22000 – An toàn thực phẩm", "2018", "2018-06-19"}};
        Map<String, UUID> schemeIds = new LinkedHashMap<>();
        for (String[] s : schemes) {
            UUID schemeId = id(admin.create("schemes", Map.of("schemeCode", s[0], "schemeName", s[1],
                    "parentCoversChild", true, "status", "ACTIVE")));
            schemeIds.put(s[0], schemeId);
            UUID stdId = id(admin.create("standards", Map.of("standardCode", s[2], "standardName", s[3],
                    "schemeId", schemeId, "status", "ACTIVE")));
            standard.put(s[2], stdId);
            admin.addStandardVersion(stdId, Map.of("version", s[4], "effectiveFrom", s[5], "status", "ACTIVE"));
        }

        // Bộ mã IAF cho QMS và EMS
        String[][] iaf = {{"01", "Nông nghiệp, lâm nghiệp và thủy sản"}, {"02", "Khai khoáng"},
                {"03", "Thực phẩm, đồ uống và thuốc lá"}, {"04", "Dệt và sản phẩm dệt"},
                {"06", "Gỗ và sản phẩm gỗ"}, {"07", "Bột giấy, giấy và sản phẩm giấy"}, {"09", "In ấn"},
                {"12", "Hóa chất, sản phẩm hóa chất và sợi"}, {"13", "Dược phẩm"},
                {"14", "Cao su và sản phẩm nhựa"}, {"16", "Bê tông, xi măng, vôi, thạch cao"},
                {"17", "Kim loại cơ bản và sản phẩm kim loại chế tạo"}, {"18", "Máy móc và thiết bị"},
                {"19", "Thiết bị điện và quang học"}, {"22", "Phương tiện vận tải khác"},
                {"25", "Cung cấp điện"}, {"27", "Cung cấp nước"}, {"28", "Xây dựng"},
                {"29", "Bán buôn, bán lẻ, sửa chữa"}, {"30", "Khách sạn và nhà hàng"},
                {"31", "Vận tải, kho bãi và truyền thông"}, {"32", "Tài chính, bất động sản, cho thuê"},
                {"33", "Công nghệ thông tin"}, {"34", "Dịch vụ kỹ thuật"}, {"35", "Dịch vụ khác"},
                {"36", "Hành chính công"}, {"37", "Giáo dục"}, {"38", "Y tế và công tác xã hội"}};
        String[][] sub = {{"17.1", "Kim loại cơ bản", "17"}, {"17.2", "Sản phẩm kim loại chế tạo", "17"},
                {"28.1", "Xây dựng dân dụng", "28"}, {"28.2", "Xây dựng công nghiệp", "28"}};
        for (String scheme : List.of("QMS", "EMS")) {
            CodeSet set = admin.createCodeSet(Map.of("schemeId", schemeIds.get(scheme), "version", "IAF-2024",
                    "effectiveFrom", "2024-01-01", "sourceRef", "IAF ID 1"));
            for (String[] c : iaf) admin.addCode(set.id(), Map.of("codeValue", c[0], "codeName", c[1]));
            for (String[] c : sub) admin.addCode(set.id(), Map.of("codeValue", c[0], "codeName", c[1], "parentCode", c[2]));
            admin.activateCodeSet(set.id());
            if (scheme.equals("QMS")) {
                for (Code c : admin.codes(set.id())) code.put(c.codeValue(), c.id());
            }
        }
    }

    // ================================================================ người dùng

    private void users() {
        // vai trò HEAD_CERTIFICATION có sẵn từ migration V14
        user("hang", "Phan Hằng", "PCN", "Nhân viên hồ sơ – VICB.005", "CERTIFICATION_MANAGER");
        user("gdcn", "Giám đốc chứng nhận", "PCN", "GĐCN – VICB.003", "CERTIFICATION_DIRECTOR");
        user("cgtruong", "Chuyên gia trưởng", "PCN", "Chuyên gia trưởng – VICB.009", "TECHNICAL_REVIEWER");
        user("tpcn", "Trưởng phòng chứng nhận", "PCN", "Trưởng phòng – VICB.174", "HEAD_CERTIFICATION");
        user("vanphong", "Nhân viên văn phòng", "VP", "Văn phòng", "DOCUMENT_CONTROLLER");
        user("an.nv", "Nguyễn Văn An", "PCN", "Chuyên gia đánh giá", "EXPERT");
        hang = login("hang", PASSWORD);
        gdcn = login("gdcn", PASSWORD);
        cgtruong = login("cgtruong", PASSWORD);
        vanphong = login("vanphong", PASSWORD);
    }

    private UUID user(String username, String name, String deptCode, String position, String role) {
        return admin.createUser(new CreateUserRequest(username, username + "@vinacert.demo", name, dept.get(deptCode),
                position, PASSWORD, List.of(role))).id();
    }

    // ================================================================ chuyên gia

    private record Exp(String field, String industry, String position, String org, String from, String to, String... codes) {}

    private record Cert(String name, String std, String issuer, LocalDate issued, LocalDate expiry) {}

    private void experts() {
        LocalDate today = LocalDate.now();
        UUID anUser = admin.users("an.nv", null, 0, 1).content().get(0).id();

        // 1. Đang hoạt động – có tài khoản đăng nhập
        UUID an = expert("Nguyễn Văn An", "1980-04-12", "MALE", "0912 345 678", "AUDITOR", "FULLTIME", "PCN", "Hà Nội",
                "Chuyên gia đánh giá trưởng", anUser,
                new String[] {"ENGINEER", "CK", "Cơ khí chế tạo máy", "ĐH Bách khoa Hà Nội", "2003"},
                List.of(new Exp("Quản lý chất lượng", "CKCT", "Trưởng phòng QA", "Công ty CP Cơ khí Hà Nội", "2004-03-01", "2014-12-31", "17", "17.2", "18"),
                        new Exp("Đánh giá chứng nhận", "CKCT", "Chuyên gia đánh giá", "VinaCert", "2015-01-05", null, "17", "18", "19")),
                List.of(new Cert("Lead Auditor ISO 9001:2015 (IRCA)", "ISO 9001", "IRCA / BSI", today.minusYears(2), today.plusYears(1).plusMonths(4)),
                        new Cert("Lead Auditor ISO 14001:2015", "ISO 14001", "SGS Academy", today.minusYears(1), today.plusYears(2))),
                Map.of("vi", "NATIVE", "en", "FLUENT"));
        approve(an);

        // 2. Đang hoạt động – chứng chỉ sắp hết hạn (cảnh báo cao)
        UUID binh = expert("Trần Thị Bình", "1985-09-23", "FEMALE", "0903 222 333", "BOTH", "FULLTIME", "PCN", "Hà Nội",
                "Chuyên gia đánh giá", null,
                new String[] {"MASTER", "CNTP", "Công nghệ thực phẩm", "ĐH Bách khoa Hà Nội", "2010"},
                List.of(new Exp("An toàn thực phẩm", "TP", "Trưởng phòng HACCP", "Công ty CP Sữa Ba Vì", "2010-07-01", "2018-06-30", "03"),
                        new Exp("Đánh giá chứng nhận", "TP", "Chuyên gia đánh giá", "VinaCert", "2018-08-01", null, "03", "30")),
                List.of(new Cert("Lead Auditor ISO 22000:2018", "ISO 22000", "Bureau Veritas", today.minusYears(3), today.plusDays(25)),
                        new Cert("Lead Auditor ISO 9001:2015", "ISO 9001", "IRCA / BSI", today.minusYears(1), today.plusYears(2))),
                Map.of("vi", "NATIVE", "en", "INTERMEDIATE"));
        approve(binh);

        // 3. Đang hoạt động – chuyên gia kỹ thuật xây dựng (bán thời gian)
        UUID cuong = expert("Lê Hoàng Cường", "1976-01-30", "MALE", "0988 456 123", "TECHNICAL_EXPERT", "PARTTIME", "PKT", "Đà Nẵng",
                "Chuyên gia kỹ thuật", null,
                new String[] {"PHD", "XD", "Kết cấu công trình", "ĐH Xây dựng", "2005"},
                List.of(new Exp("Thiết kế kết cấu", "XD", "Chủ nhiệm thiết kế", "Tổng Công ty Tư vấn Xây dựng Việt Nam", "2000-01-01", "2016-12-31", "28", "28.1", "28.2", "16")),
                List.of(new Cert("Chứng chỉ hành nghề thiết kế kết cấu hạng I", null, "Bộ Xây dựng", today.minusYears(4), today.plusYears(1))),
                Map.of("vi", "NATIVE"));
        approve(cuong);

        // 4. Đang hoạt động – chứng chỉ sắp hết hạn khẩn cấp (≤ 7 ngày)
        UUID duc = expert("Phạm Minh Đức", "1988-11-05", "MALE", "0977 888 999", "AUDITOR", "FULLTIME", "CN-HCM", "TP. Hồ Chí Minh",
                "Chuyên gia đánh giá", null,
                new String[] {"BACHELOR", "CNTT", "Hệ thống thông tin", "ĐH Quốc gia TP.HCM", "2010"},
                List.of(new Exp("Quản lý vận hành", "LOG", "Trưởng bộ phận kho vận", "Công ty TNHH Logistics Sài Gòn", "2011-01-01", "2017-12-31", "31", "29"),
                        new Exp("Đánh giá chứng nhận", "CNTT", "Chuyên gia đánh giá", "VinaCert – Chi nhánh HCM", "2018-02-01", null, "29", "31", "33")),
                List.of(new Cert("Lead Auditor ISO 9001:2015", "ISO 9001", "TÜV Rheinland", today.minusYears(3), today.plusDays(6)),
                        new Cert("Lead Auditor ISO 45001:2018", "ISO 45001", "TÜV Rheinland", today.minusYears(1), today.plusYears(2))),
                Map.of("vi", "NATIVE", "en", "FLUENT", "ja", "BASIC"));
        approve(duc);

        // 5. Tạm dừng đánh giá có thời hạn
        UUID em = expert("Hoàng Thị Em", "1990-06-18", "FEMALE", "0934 111 222", "AUDITOR", "PARTTIME", "PCN", "Hải Phòng",
                "Chuyên gia đánh giá", null,
                new String[] {"ENGINEER", "MT", "Kỹ thuật môi trường", "ĐH Hàng hải Việt Nam", "2012"},
                List.of(new Exp("Môi trường", "HC", "Kỹ sư môi trường", "Công ty CP Hóa chất Hải Phòng", "2012-09-01", null, "12", "14")),
                List.of(new Cert("Lead Auditor ISO 14001:2015", "ISO 14001", "SGS Academy", today.minusYears(2), today.plusYears(1))),
                Map.of("vi", "NATIVE", "en", "INTERMEDIATE"));
        approve(em);
        gdcn.changeExpertStatus(em, "SUSPEND", "Chờ kết quả witness bổ sung", today.plusDays(45));

        // 6. Ngừng hoạt động
        UUID phong = expert("Vũ Quốc Phong", "1962-02-14", "MALE", "0913 777 666", "AUDITOR", "FULLTIME", "PCN", "Bắc Ninh",
                "Chuyên gia đánh giá", null,
                new String[] {"ENGINEER", "DDT", "Kỹ thuật điện", "ĐH Bách khoa Hà Nội", "1985"},
                List.of(new Exp("Sản xuất thiết bị điện", "DDT", "Phó giám đốc kỹ thuật", "Công ty Thiết bị điện Đông Anh", "1986-01-01", "2012-12-31", "19", "25")),
                List.of(), Map.of("vi", "NATIVE"));
        approve(phong);
        gdcn.changeExpertStatus(phong, "DEACTIVATE", "Nghỉ hưu, không tiếp tục cộng tác", null);

        // 7. Chờ Chuyên gia trưởng thẩm tra;  8. Đã thẩm tra, chờ GĐCN phê duyệt
        UUID giang = expert("Đặng Thu Giang", "1992-03-08", "FEMALE", "0965 432 100", "AUDITOR", "FULLTIME", "PCN", "Hà Nội",
                "Chuyên gia đánh giá", null,
                new String[] {"MASTER", "QTKD", "Quản trị kinh doanh", "ĐH Kinh tế Quốc dân", "2016"},
                List.of(new Exp("Hệ thống quản lý", "TM", "Chuyên viên ISO", "Tập đoàn Bán lẻ Việt", "2016-05-01", "2023-04-30", "29", "35")),
                List.of(new Cert("Lead Auditor ISO 9001:2015", "ISO 9001", "IRCA / BSI", today.minusMonths(5), today.plusYears(3))),
                Map.of("vi", "NATIVE", "en", "FLUENT"));
        hang.changeExpertStatus(giang, "SUBMIT", "Hồ sơ mới, đủ tài liệu", null);

        UUID hai = expert("Bùi Văn Hải", "1983-12-01", "MALE", "0916 543 210", "TECHNICAL_EXPERT", "PARTTIME", "PKT", "Bình Dương",
                "Chuyên gia kỹ thuật", null,
                new String[] {"ENGINEER", "HH", "Công nghệ hóa học", "ĐH Bách khoa TP.HCM", "2006"},
                List.of(new Exp("Sản xuất hóa chất", "HC", "Quản đốc", "Công ty Hóa chất Miền Nam", "2006-08-01", null, "12", "13", "14")),
                List.of(), Map.of("vi", "NATIVE", "en", "BASIC"));
        hang.changeExpertStatus(hai, "SUBMIT", null, null);
        cgtruong.changeExpertStatus(hai, "REVIEW", "Đủ năng lực TE cho code 12, 13, 14", null);

        // 9. Bị Chuyên gia trưởng trả lại, yêu cầu bổ sung
        UUID lan = expert("Ngô Thị Lan", "1991-07-27", "FEMALE", "0946 888 123", "AUDITOR", "FULLTIME", "PCN", "Cần Thơ",
                "Chuyên gia đánh giá", null,
                new String[] {"BACHELOR", "CNTP", "Công nghệ thực phẩm", "ĐH Cần Thơ", "2013"},
                List.of(new Exp("Kiểm soát chất lượng", "TP", "Nhân viên QC", "Công ty Thủy sản Cửu Long", "2013-10-01", null, "03", "01")),
                List.of(), Map.of("vi", "NATIVE"));
        hang.changeExpertStatus(lan, "SUBMIT", null, null);
        cgtruong.changeExpertStatus(lan, "RETURN",
                "Bổ sung chứng chỉ đánh giá viên ISO 22000 và xác nhận kinh nghiệm của đơn vị công tác", null);

        // 10. Hồ sơ nháp chưa đủ (chưa có kinh nghiệm)
        expert("Đỗ Mạnh Khang", "1995-05-15", "MALE", "0987 000 111", "AUDITOR", "PARTTIME", "CN-HCM", "TP. Hồ Chí Minh",
                null, null, new String[] {"BACHELOR", "MT", "Khoa học môi trường", "ĐH Khoa học Tự nhiên TP.HCM", "2017"},
                List.of(), List.of(), Map.of("vi", "NATIVE"));
    }

    /** NV hồ sơ lập hồ sơ đầy đủ: thông tin chung, học vấn, kinh nghiệm + code, đào tạo, chứng chỉ, ngôn ngữ, tài liệu. */
    private UUID expert(String name, String dob, String gender, String phone, String type, String employment,
                        String deptCode, String location, String position, UUID userId, String[] edu,
                        List<Exp> exps, List<Cert> certs, Map<String, String> langs) {
        String email = slug(name) + "@vinacert.demo";
        ExpertDetail e = hang.createExpert(new ExpertRequest(name, LocalDate.parse(dob), gender, null, location, phone, email,
                type, employment, dept.get(deptCode), position, LocalDate.now().minusYears(3), loc.get(location), userId,
                "FULLTIME".equals(employment) ? new BigDecimal("18") : new BigDecimal("8")));
        UUID id = e.id();

        UUID diploma = verifiedDoc(id, "EDUCATION", "Bằng " + edu[2] + " – " + name, null);
        hang.createExpertItem(id, "educations", new EducationRequest(edu[0], field.get(edu[1]), edu[2], edu[3],
                Short.valueOf(edu[4]), diploma, true));
        for (Exp x : exps) {
            List<UUID> codeIds = new ArrayList<>();
            for (String c : x.codes()) codeIds.add(code.get(c));
            boolean current = x.to() == null;
            hang.createExpertItem(id, "experiences", new ExperienceRequest(industry.get(x.industry()), x.field(), x.position(),
                    x.org(), LocalDate.parse(x.from()), current ? null : LocalDate.parse(x.to()), current,
                    current ? LocalDate.now() : null, null, null, codeIds));
        }
        for (Cert c : certs) {
            UUID scan = verifiedDoc(id, "CERTIFICATE", c.name() + " – " + name, c.expiry());
            UUID std = c.std() == null ? null : standard.get(c.std());
            hang.createExpertItem(id, "certificates", new CertificateRequest(c.name(), "CC-" + (1000 + docSeq), c.issuer(),
                    std, c.issued(), c.expiry(), scan, "VALID"));
            if (std != null) {
                hang.createExpertItem(id, "trainings", new TrainingRequest("Khoá " + c.name(), c.issuer(), std, "LEAD_AUDITOR",
                        c.issued().minusDays(5), c.issued().minusDays(1), new BigDecimal("40"), null, null, null));
            }
        }
        langs.forEach((lang, level) -> hang.updateExpertItem(id, "languages", lang,
                new LanguageRequest(level, !"BASIC".equals(level))));
        verifiedDoc(id, "CV", "Sơ yếu lý lịch – " + name, null);
        verifiedDoc(id, "NDA", "Cam kết bảo mật – " + name, LocalDate.now().plusYears(1));
        return id;
    }

    private void approve(UUID expertId) {
        hang.changeExpertStatus(expertId, "SUBMIT", "Đủ hồ sơ theo BM F01-08-04", null);
        cgtruong.changeExpertStatus(expertId, "REVIEW", "Đạt", null);
        gdcn.changeExpertStatus(expertId, "APPROVE", "Đồng ý", null);
    }

    /** Tải lên một tệp PDF mẫu (nội dung khác nhau để không bị coi là trùng) và cho Văn phòng xác minh. */
    private UUID verifiedDoc(UUID expertId, String type, String title, LocalDate expiry) {
        try {
            Path file = tmp.resolve("tai-lieu-" + (++docSeq) + ".pdf");
            Files.write(file, pdf(title + " #" + docSeq));
            UploadResult r = hang.uploadDocument(file, type, title, expertId, LocalDate.now().minusMonths(1), expiry, null, null);
            vanphong.verifyVersion(r.document().currentVersion().id());
            return r.document().id();
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    // ================================================================ năng lực

    private final Map<String, UUID> stdId = new HashMap<>();
    private final Map<String, UUID> roleId = new HashMap<>();
    /** khoá "ISO 9001|17|AU" (code "*" = toàn tiêu chuẩn) → id định nghĩa năng lực */
    private final Map<String, UUID> defId = new HashMap<>();

    private void competencyDefinitions() {
        admin.standards(null).forEach(st -> stdId.put(st.standardCode(), st.id()));
        admin.assessmentRoles().forEach(r -> roleId.put(r.roleCode(), r.id()));
        LocalDate from = LocalDate.of(2024, 1, 1);
        var la = criteria("BACHELOR", 4, 4, "Khoá Lead Auditor được công nhận (IRCA/Exemplar Global)");
        var au = criteria("BACHELOR", 2, 2, "Khoá đánh giá viên / Lead Auditor");
        var auCode = criteria("BACHELOR", 2, 2, "Kinh nghiệm làm việc trong lĩnh vực của code");
        var te = criteria("ENGINEER", 5, null, "Chuyên môn sâu trong lĩnh vực của code");
        for (String std : stdId.keySet()) {
            bulk(std, "LA", List.of(), true, (short) 36, from, la);
            bulk(std, "AU", List.of(), true, (short) 36, from, au);
        }
        for (String std : List.of("ISO 9001", "ISO 14001")) {
            List<UUID> codeIds = codesOfStandard(std);
            bulk(std, "AU", codeIds, false, (short) 36, from, auCode);
            bulk(std, "TE", codeIds, false, (short) 36, from, te);
        }
        for (var d : admin.activeCompetencyDefinitions(null)) {
            defId.put(d.standardCode() + "|" + (d.codeValue() == null ? "*" : d.codeValue()) + "|" + d.roleCode(), d.id());
        }
    }

    private void bulk(String std, String role, List<UUID> codeIds, boolean general, short months, LocalDate from,
                      com.fasterxml.jackson.databind.JsonNode criteria) {
        admin.createCompetencyDefinitionsBulk(new com.npcore.ems.desktop.api.Dtos.BulkDefinitionRequest(stdId.get(std),
                roleId.get(role), codeIds, general, months, from, "1", criteria));
    }

    private List<UUID> codesOfStandard(String std) {
        var st = admin.standards(null).stream().filter(s -> s.standardCode().equals(std)).findFirst().orElseThrow();
        List<UUID> ids = new ArrayList<>();
        for (var set : admin.codeSets(st.schemeId())) {
            if ("ACTIVE".equals(set.status())) admin.codes(set.id()).forEach(c -> ids.add(c.id()));
        }
        return ids;
    }

    private static com.fasterxml.jackson.databind.JsonNode criteria(String degree, Integer years, Integer audits, String training) {
        var n = com.npcore.ems.desktop.api.Json.MAPPER.createObjectNode();
        n.put("minDegreeLevel", degree);
        if (years != null) n.put("minYearsExperience", years);
        if (audits != null) n.put("minAudits", audits);
        n.put("requiredTraining", training);
        return n;
    }

    private void expertCompetencies() {
        LocalDate today = LocalDate.now();
        // Đã phê duyệt
        approved("Nguyễn Văn An", "ISO 9001|*|LA", "ISO 9001|17|AU", "ISO 9001|18|AU", "ISO 9001|19|AU", "ISO 14001|*|AU");
        approved("Trần Thị Bình", "ISO 22000|*|LA", "ISO 9001|03|AU", "ISO 9001|30|AU");
        approved("Lê Hoàng Cường", "ISO 9001|28|TE", "ISO 9001|16|TE");          // 28 → 28.1, 28.2 (code cha bao con)
        approved("Phạm Minh Đức", "ISO 9001|*|LA", "ISO 9001|29|AU", "ISO 9001|31|AU", "ISO 45001|*|AU");
        approved("Hoàng Thị Em", "ISO 14001|12|AU", "ISO 14001|14|AU");         // chuyên gia đang tạm dừng
        // Sắp hết hạn (còn 30 ngày) và đã hết hạn
        competency("Phạm Minh Đức", "ISO 9001|33|AU", today.minusYears(3).plusDays(30), today.plusDays(30), "APPROVE");
        competency("Nguyễn Văn An", "ISO 9001|25|AU", today.minusYears(4), today.minusDays(20), "APPROVE");
        // Đang trong quy trình
        competency("Đặng Thu Giang", "ISO 9001|29|AU", null, null, "SUBMIT");
        competency("Bùi Văn Hải", "ISO 9001|12|TE", null, null, "START_REVIEW");
        competency("Ngô Thị Lan", "ISO 22000|*|AU", null, null, null);
    }

    private void approved(String expert, String... keys) {
        for (String k : keys) competency(expert, k, null, null, "APPROVE");
    }

    /** NV hồ sơ đăng ký + minh chứng; đi quy trình tới bước 'until': SUBMIT (hang) → START_REVIEW (cgtruong) → APPROVE (gdcn). */
    private void competency(String expertName, String key, LocalDate from, LocalDate to, String until) {
        UUID expertId = hang.experts(expertName, null, null, null, 0, 1, null).content().get(0).id();
        UUID def = defId.get(key);
        if (def == null) throw new IllegalStateException("Thiếu định nghĩa năng lực " + key);
        var ec = hang.addExpertCompetency(expertId, new com.npcore.ems.desktop.api.Dtos.ExpertCompetencyRequest(def, null, null,
                null, null, key.endsWith("|LA") ? "SENIOR" : "QUALIFIED", from, to, null));
        hang.addCompetencyEvidence(expertId, ec.id(), new com.npcore.ems.desktop.api.Dtos.EvidenceRequest(null, "EXPERIENCE",
                null, null, "Kinh nghiệm làm việc và số cuộc đánh giá theo hồ sơ"));
        hang.addCompetencyEvidence(expertId, ec.id(), new com.npcore.ems.desktop.api.Dtos.EvidenceRequest(null, "CERTIFICATE",
                null, null, "Chứng chỉ đánh giá viên còn hiệu lực"));
        if (until == null) return;
        hang.transitionExpertCompetency(expertId, ec.id(), "SUBMIT", "Đủ minh chứng");
        if (until.equals("SUBMIT")) return;
        cgtruong.transitionExpertCompetency(expertId, ec.id(), "START_REVIEW", null);
        if (until.equals("START_REVIEW")) return;
        gdcn.transitionExpertCompetency(expertId, ec.id(), "APPROVE", "Đồng ý");
    }

    /** PDF 1 trang tối giản (chữ không dấu). */
    static byte[] pdf(String text) {
        String t = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replace('đ', 'd').replace('Đ', 'D').replaceAll("[^\\x20-\\x7E]", "").replace("(", "[").replace(")", "]");
        String stream = "BT /F1 14 Tf 60 760 Td (EMS DEMO) Tj 0 -24 Td (" + t + ") Tj ET";
        String[] objs = {
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>",
                "<< /Length " + stream.length() + " >>\nstream\n" + stream + "\nendstream",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"};
        StringBuilder sb = new StringBuilder("%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objs.length; i++) {
            offsets.add(sb.length());
            sb.append(i + 1).append(" 0 obj\n").append(objs[i]).append("\nendobj\n");
        }
        int xref = sb.length();
        sb.append("xref\n0 ").append(objs.length + 1).append("\n0000000000 65535 f \n");
        for (int o : offsets) sb.append(String.format("%010d 00000 n \n", o));
        sb.append("trailer\n<< /Size ").append(objs.length + 1).append(" /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        return sb.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    private static String slug(String name) {
        String s = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replace('đ', 'd').replace('Đ', 'D').toLowerCase();
        String[] p = s.split("\\s+");
        StringBuilder sb = new StringBuilder(p[p.length - 1]).append('.');
        for (int i = 0; i < p.length - 1; i++) sb.append(p[i].charAt(0));
        return sb.toString();
    }

    private static UUID id(JsonNode n) {
        return UUID.fromString(n.path("id").asText());
    }
}
