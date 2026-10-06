package com.npcore.ems.desktop.ui.fx;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/** Định dạng hiển thị (ngày dd/MM/yyyy) và nhãn tiếng Việt cho các mã trạng thái. */
public final class Fmt {

    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    public static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final Map<String, String> LABELS = Map.ofEntries(
            // trạng thái chuyên gia / chung
            Map.entry("DRAFT", "Nháp"), Map.entry("ACTIVE", "Đang hoạt động"), Map.entry("SUSPENDED", "Tạm dừng"),
            Map.entry("INACTIVE", "Ngừng hoạt động"), Map.entry("DISABLED", "Vô hiệu hoá"), Map.entry("LOCKED", "Bị khoá"),
            Map.entry("RETIRED", "Hết hiệu lực"), Map.entry("TRANSITION", "Chuyển đổi"), Map.entry("WITHDRAWN", "Thu hồi"),
            // loại chuyên gia / hợp đồng
            Map.entry("AUDITOR", "Chuyên gia đánh giá"), Map.entry("TECHNICAL_EXPERT", "Chuyên gia kỹ thuật"),
            Map.entry("BOTH", "Đánh giá + Kỹ thuật"), Map.entry("FULLTIME", "Toàn thời gian"),
            Map.entry("PARTTIME", "Bán thời gian"), Map.entry("MALE", "Nam"), Map.entry("FEMALE", "Nữ"),
            Map.entry("OTHER", "Khác"),
            // tài liệu
            Map.entry("PENDING_VERIFICATION", "Chờ xác minh"), Map.entry("VERIFIED", "Đã xác minh"),
            Map.entry("REJECTED", "Bị từ chối"), Map.entry("SUPERSEDED", "Đã thay thế"), Map.entry("EXPIRED", "Hết hạn"),
            Map.entry("VALID", "Còn hiệu lực"), Map.entry("REVOKED", "Thu hồi"),
            // cảnh báo hết hạn
            Map.entry("NONE", ""), Map.entry("WARNING", "Sắp hết hạn"), Map.entry("HIGH", "Sắp hết hạn (cao)"),
            Map.entry("CRITICAL", "Sắp hết hạn (khẩn)"),
            // hành động trạng thái
            Map.entry("ACTIVATE", "Kích hoạt"), Map.entry("SUSPEND", "Tạm dừng"), Map.entry("REINSTATE", "Khôi phục"),
            Map.entry("DEACTIVATE", "Ngừng hoạt động"), Map.entry("REACTIVATE", "Kích hoạt lại"),
            Map.entry("CREATE", "Tạo mới"), Map.entry("UPDATE", "Cập nhật"), Map.entry("IMPORT", "Import"),
            // đào tạo / ngôn ngữ
            Map.entry("LEAD_AUDITOR", "Lead Auditor"), Map.entry("INTERNAL_AUDITOR", "Đánh giá viên nội bộ"),
            Map.entry("TECHNICAL", "Kỹ thuật"), Map.entry("CALIBRATION", "Hiệu chuẩn"), Map.entry("REFRESHER", "Cập nhật kiến thức"),
            Map.entry("BASIC", "Cơ bản"), Map.entry("INTERMEDIATE", "Trung bình"), Map.entry("FLUENT", "Thành thạo"),
            Map.entry("NATIVE", "Bản ngữ"));

    private Fmt() {}

    public static String label(String code) {
        if (code == null) return "";
        return LABELS.getOrDefault(code, code);
    }

    public static String date(LocalDate d) {
        return d == null ? "" : DATE.format(d);
    }

    public static String dateTime(OffsetDateTime t) {
        return t == null ? "" : DATE_TIME.format(t.atZoneSameInstant(ZoneId.systemDefault()));
    }

    public static String text(Object o) {
        if (o == null) return "";
        if (o instanceof BigDecimal b) return b.stripTrailingZeros().toPlainString();
        return o.toString();
    }

    public static String size(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / 1024.0 / 1024.0);
    }

    /** CSS class cho nhãn trạng thái: ok / warn / danger / muted. */
    public static String tone(String code) {
        if (code == null) return "muted";
        return switch (code) {
            case "ACTIVE", "VERIFIED", "VALID" -> "ok";
            case "SUSPENDED", "PENDING_VERIFICATION", "WARNING", "HIGH", "LOCKED" -> "warn";
            case "CRITICAL", "EXPIRED", "REJECTED", "REVOKED", "DISABLED" -> "danger";
            default -> "muted";
        };
    }
}
