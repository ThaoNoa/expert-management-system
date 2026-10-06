package com.npcore.ems.desktop.ui.expert;

import com.npcore.ems.desktop.api.Dtos.ExpertDetail;
import com.npcore.ems.desktop.api.Dtos.ExpertRequest;
import com.npcore.ems.desktop.api.Dtos.User;
import com.npcore.ems.desktop.api.Session;
import com.npcore.ems.desktop.ui.fx.Async;
import com.npcore.ems.desktop.ui.fx.Form;
import com.npcore.ems.desktop.ui.fx.Form.Option;
import com.npcore.ems.desktop.ui.fx.Lookups;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import javafx.scene.Node;

/** Hộp thoại thêm / sửa thông tin chung của chuyên gia. */
final class ExpertForm {

    private ExpertForm() {}

    /** existing == null → tạo mới. adminFields = người sửa có quyền trên toàn bộ dữ liệu. */
    static void open(Session session, Node busy, ExpertDetail existing, Consumer<ExpertDetail> onSaved) {
        boolean admin = session.hasAll("EXPERT_EDIT") || existing == null;
        boolean canLinkUser = session.has("USER_MANAGE");
        Async.run(busy, () -> new Data(Lookups.load(session),
                canLinkUser ? session.api().users(null, "ACTIVE", 0, 500).content() : List.<User>of()), data -> {
            Form f = new Form()
                    .text("fullName", "Họ và tên", true)
                    .date("dateOfBirth", "Ngày sinh", false)
                    .codes("gender", "Giới tính", List.of("MALE", "FEMALE", "OTHER"), false)
                    .text("idNumber", "Số CCCD/CMND", false)
                    .text("phone", "Điện thoại", false)
                    .text("email", "Email", false)
                    .area("address", "Địa chỉ", false)
                    .choice("homeLocationId", "Địa điểm thường trú", data.lookups().locationOptions(), false)
                    .codes("expertType", "Loại chuyên gia", List.of("AUDITOR", "TECHNICAL_EXPERT", "BOTH"), true)
                    .codes("employmentType", "Hình thức hợp đồng", List.of("FULLTIME", "PARTTIME"), true)
                    .choice("departmentId", "Phòng ban", data.lookups().departmentOptions(), false)
                    .text("position", "Chức vụ", false)
                    .date("joinedDate", "Ngày tham gia", false)
                    .text("maxMandaysPerMonth", "Manday tối đa/tháng", false);
            if (canLinkUser) {
                List<Option<UUID>> users = Lookups.withNone(data.users().stream()
                        .map(u -> new Option<>(u.id(), u.username() + " – " + u.fullName())).toList());
                f.choice("userId", "Tài khoản đăng nhập", users, false);
            }
            if (existing == null) {
                f.note("Mã chuyên gia (FT-xxxx / PT-xxx) được hệ thống tự cấp theo hình thức hợp đồng.");
            } else {
                fill(f, existing, canLinkUser);
                if (!admin) {
                    for (String k : List.of("expertType", "employmentType", "departmentId", "position", "joinedDate", "maxMandaysPerMonth")) {
                        f.disable(k, true);
                    }
                    f.note("Bạn chỉ sửa được thông tin cá nhân; loại chuyên gia, hợp đồng, phòng ban do bộ phận quản lý cập nhật.");
                }
            }
            f.showDialog(existing == null ? "Thêm chuyên gia" : "Sửa thông tin – " + existing.expertCode(), "Lưu", () -> {
                ExpertRequest r = new ExpertRequest(f.str("fullName"), f.date("dateOfBirth"), f.value("gender"),
                        f.str("idNumber"), f.str("address"), f.str("phone"), f.str("email"), f.value("expertType"),
                        f.value("employmentType"), f.value("departmentId"), f.str("position"), f.date("joinedDate"),
                        f.value("homeLocationId"),
                        canLinkUser ? f.value("userId") : (existing == null ? null : existing.userId()),
                        f.decimal("maxMandaysPerMonth"));
                return existing == null ? session.api().createExpert(r) : session.api().updateExpert(existing.id(), r);
            }, onSaved);
        });
    }

    private static void fill(Form f, ExpertDetail e, boolean canLinkUser) {
        f.set("fullName", e.fullName()).set("dateOfBirth", e.dateOfBirth()).set("gender", e.gender())
                .set("idNumber", e.idNumber()).set("phone", e.phone()).set("email", e.email())
                .set("address", e.address()).set("homeLocationId", e.homeLocationId())
                .set("expertType", e.expertType()).set("employmentType", e.employmentType())
                .set("departmentId", e.departmentId()).set("position", e.position())
                .set("joinedDate", e.joinedDate()).set("maxMandaysPerMonth", e.maxMandaysPerMonth());
        if (canLinkUser) f.set("userId", e.userId());
    }

    private record Data(Lookups lookups, List<User> users) {}
}
