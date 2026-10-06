package com.npcore.ems.desktop.ui.admin;

import com.npcore.ems.desktop.api.Dtos.CreateUserRequest;
import com.npcore.ems.desktop.api.Dtos.Department;
import com.npcore.ems.desktop.api.Dtos.Role;
import com.npcore.ems.desktop.api.Dtos.UpdateUserRequest;
import com.npcore.ems.desktop.api.Dtos.User;
import com.npcore.ems.desktop.api.Session;
import com.npcore.ems.desktop.ui.fx.Async;
import com.npcore.ems.desktop.ui.fx.Dialogs;
import com.npcore.ems.desktop.ui.fx.Fmt;
import com.npcore.ems.desktop.ui.fx.Form;
import com.npcore.ems.desktop.ui.fx.Form.Option;
import com.npcore.ems.desktop.ui.fx.Lookups;
import com.npcore.ems.desktop.ui.fx.PagedTable;
import com.npcore.ems.desktop.ui.fx.Tables;
import com.npcore.ems.desktop.ui.fx.Ui;
import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Quản lý người dùng (FR-1.1): tạo, sửa, gán vai trò, vô hiệu hoá / mở khoá, đặt lại mật khẩu, xoá mềm. */
public final class UsersView extends VBox {

    private final Session session;
    private List<Role> roles = List.of();
    private List<Department> departments = List.of();

    public UsersView(Session session) {
        super(12);
        this.session = session;
        setPadding(new Insets(16));
        TextField q = new TextField();
        q.setPromptText("Tìm theo tên đăng nhập, họ tên, email");
        q.setPrefWidth(300);
        ComboBox<Option<String>> status = new ComboBox<>();
        List<Option<String>> st = new ArrayList<>();
        st.add(new Option<>(null, "Tất cả trạng thái"));
        for (String c : List.of("ACTIVE", "DISABLED", "LOCKED")) st.add(new Option<>(c, Fmt.label(c)));
        status.getItems().setAll(st);
        status.setPromptText("Trạng thái");

        TableView<User> table = Tables.table("Không có người dùng");
        table.getColumns().addAll(List.of(
                Tables.col("Tên đăng nhập", User::username, 140), Tables.col("Họ tên", User::fullName, 200),
                Tables.col("Email", User::email, 200), Tables.col("Phòng ban", User::departmentName, 140),
                Tables.col("Vai trò", u -> String.join(", ", u.roleCodes()), 220),
                Tables.status("Trạng thái", User::status, 120),
                Tables.col("Đăng nhập gần nhất", u -> Fmt.dateTime(u.lastLoginAt()), 140)));
        PagedTable<User> paged = new PagedTable<>(table, (page, size) -> session.api().users(
                q.getText().isBlank() ? null : q.getText().trim(), status.getValue() == null ? null : status.getValue().value(), page, size));
        q.setOnAction(e -> paged.reload());
        status.setOnAction(e -> paged.reload());

        Button add = Ui.primary("+ Thêm người dùng", () -> form(null, paged));
        Button edit = Ui.button("Sửa", () -> form(table.getSelectionModel().getSelectedItem(), paged));
        Button toggle = Ui.button("Khoá / Mở khoá", () -> {
            User u = table.getSelectionModel().getSelectedItem();
            boolean enable = !"ACTIVE".equals(u.status());
            if (Dialogs.confirm((enable ? "Mở khoá" : "Vô hiệu hoá") + " tài khoản " + u.username() + "?")) {
                Async.exec(this, () -> session.api().setUserEnabled(u.id(), enable), paged::refresh);
            }
        });
        Button reset = Ui.button("Đặt lại mật khẩu", () -> {
            User u = table.getSelectionModel().getSelectedItem();
            Form f = new Form().password("p", "Mật khẩu mới", true).note("Tối thiểu 8 ký tự, gồm chữ, số và ký tự đặc biệt.");
            f.showDialog("Đặt lại mật khẩu – " + u.username(), "Lưu", () -> {
                session.api().resetPassword(u.id(), f.str("p"));
                return Boolean.TRUE;
            }, ok -> Dialogs.info("Đã đặt lại mật khẩu cho " + u.username()));
        });
        Button delete = Ui.danger("Xoá", () -> {
            User u = table.getSelectionModel().getSelectedItem();
            if (Dialogs.confirm("Xoá tài khoản " + u.username() + "? (xoá mềm, lịch sử vẫn được giữ)")) {
                Async.exec(this, () -> session.api().deleteUser(u.id()), paged::refresh);
            }
        });
        for (Button b : List.of(edit, toggle, reset, delete)) {
            b.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        }
        Tables.onOpen(table, u -> form(u, paged));

        VBox.setVgrow(paged, Priority.ALWAYS);
        getChildren().addAll(Ui.title("Người dùng"),
                Ui.toolbar(q, status, Ui.button("Tìm", paged::reload), Ui.spacer(), edit, toggle, reset, delete, add), paged);
        Async.run(this, () -> new Object[] {session.api().roles(), session.api().departments()}, r -> {
            @SuppressWarnings("unchecked") List<Role> rl = (List<Role>) r[0];
            @SuppressWarnings("unchecked") List<Department> dl = (List<Department>) r[1];
            roles = rl;
            departments = dl;
        });
        paged.reload();
    }

    private void form(User existing, PagedTable<User> paged) {
        Form f = new Form();
        if (existing == null) f.text("username", "Tên đăng nhập", true);
        f.text("fullName", "Họ tên", true).text("email", "Email", true)
                .choice("department", "Phòng ban", Lookups.withNone(departments.stream()
                        .map(d -> new Option<>(d.id(), d.departmentCode() + " – " + d.departmentName())).toList()), false)
                .text("position", "Chức vụ", false)
                .multi("roles", "Vai trò", roles.stream().map(r -> new Option<>(r.roleCode(), r.roleCode() + " – " + r.roleName())).toList());
        if (existing == null) {
            f.password("password", "Mật khẩu ban đầu", true).note("Mật khẩu tối thiểu 8 ký tự, gồm chữ, số và ký tự đặc biệt. Giữ Ctrl để chọn nhiều vai trò.");
        } else {
            f.set("fullName", existing.fullName()).set("email", existing.email()).set("department", existing.departmentId())
                    .set("position", existing.position()).selectValues("roles", existing.roleCodes());
        }
        f.showDialog(existing == null ? "Thêm người dùng" : "Sửa người dùng – " + existing.username(), "Lưu", () -> {
            List<String> rs = f.values("roles");
            if (rs.isEmpty()) throw new com.npcore.ems.desktop.api.ApiException(400, "VALIDATION_ERROR", "Chọn ít nhất một vai trò", null);
            return existing == null
                    ? session.api().createUser(new CreateUserRequest(f.str("username"), f.str("email"), f.str("fullName"),
                    f.value("department"), f.str("position"), f.str("password"), rs))
                    : session.api().updateUser(existing.id(), new UpdateUserRequest(f.str("email"), f.str("fullName"),
                    f.value("department"), f.str("position"), rs));
        }, u -> paged.refresh());
    }
}
