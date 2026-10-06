package com.npcore.ems.desktop.ui.admin;

import com.npcore.ems.desktop.api.Dtos.Grant;
import com.npcore.ems.desktop.api.Dtos.Permission;
import com.npcore.ems.desktop.api.Dtos.Role;
import com.npcore.ems.desktop.api.Dtos.RoleRequest;
import com.npcore.ems.desktop.api.Session;
import com.npcore.ems.desktop.ui.fx.Async;
import com.npcore.ems.desktop.ui.fx.Dialogs;
import com.npcore.ems.desktop.ui.fx.Form;
import com.npcore.ems.desktop.ui.fx.Tables;
import com.npcore.ems.desktop.ui.fx.Ui;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Vai trò & quyền (FR-1.2/1.3): phân quyền theo bảng, có phạm vi dữ liệu ALL / DEPARTMENT / OWN. */
public final class RolesView extends VBox {

    private final Session session;
    private final TableView<Role> table = Tables.table("Chưa có vai trò");
    private List<Permission> permissions = List.of();

    public RolesView(Session session) {
        super(12);
        this.session = session;
        setPadding(new Insets(16));
        table.getColumns().addAll(List.of(
                Tables.col("Mã", Role::roleCode, 200), Tables.col("Tên vai trò", Role::roleName, 220),
                Tables.col("Số quyền", r -> r.permissions().size(), 80),
                Tables.col("Hệ thống", r -> r.system() ? "✓" : "", 80), Tables.col("Mô tả", Role::description, 300)));
        Button add = Ui.primary("+ Thêm vai trò", () -> form(null));
        Button edit = Ui.button("Sửa quyền", () -> form(table.getSelectionModel().getSelectedItem()));
        Button delete = Ui.danger("Xoá", () -> {
            Role r = table.getSelectionModel().getSelectedItem();
            if (Dialogs.confirm("Xoá vai trò " + r.roleCode() + "?")) Async.exec(this, () -> session.api().deleteRole(r.id()), this::reload);
        });
        edit.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        delete.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        Tables.onOpen(table, this::form);
        VBox.setVgrow(table, Priority.ALWAYS);
        getChildren().addAll(Ui.title("Vai trò & quyền"), Ui.toolbar(Ui.button("Tải lại", this::reload), Ui.spacer(), edit, delete, add),
                table, Ui.hint("Vai trò hệ thống không xoá được; vai trò đang gán cho người dùng cũng không xoá được. "
                        + "Phạm vi OWN = chỉ dữ liệu của chính mình (dùng cho chuyên gia)."));
        reload();
    }

    private void reload() {
        Async.run(this, () -> new Object[] {session.api().roles(), session.api().permissions()}, r -> {
            @SuppressWarnings("unchecked") List<Role> rl = (List<Role>) r[0];
            @SuppressWarnings("unchecked") List<Permission> pl = (List<Permission>) r[1];
            table.getItems().setAll(rl);
            permissions = pl;
        });
    }

    private void form(Role existing) {
        Form f = new Form().text("code", "Mã vai trò (A-Z, 0-9, _)", true).text("name", "Tên vai trò", true)
                .area("description", "Mô tả", false);
        if (existing != null) {
            f.set("code", existing.roleCode()).set("name", existing.roleName()).set("description", existing.description())
                    .disable("code", true);
        }
        Map<String, String> current = new LinkedHashMap<>();
        if (existing != null) existing.permissions().forEach(g -> current.put(g.code(), g.dataScope()));

        Map<String, CheckBox> checks = new LinkedHashMap<>();
        Map<String, ComboBox<String>> scopes = new LinkedHashMap<>();
        Map<String, List<Permission>> byModule = new LinkedHashMap<>();
        permissions.forEach(p -> byModule.computeIfAbsent(p.module(), k -> new ArrayList<>()).add(p));
        VBox matrix = new VBox(6);
        byModule.forEach((module, perms) -> {
            GridPane g = new GridPane();
            g.setHgap(12);
            g.setVgap(4);
            int row = 0;
            for (Permission p : perms) {
                CheckBox cb = new CheckBox(p.code());
                cb.setMnemonicParsing(false);
                cb.setMinWidth(230);
                cb.setPrefWidth(230);
                cb.setSelected(current.containsKey(p.code()));
                ComboBox<String> scope = new ComboBox<>();
                scope.getItems().setAll("ALL", "DEPARTMENT", "OWN");
                scope.setValue(current.getOrDefault(p.code(), "ALL"));
                scope.disableProperty().bind(cb.selectedProperty().not());
                g.add(cb, 0, row);
                Label name = new Label(p.name());
                name.setMinWidth(260);
                name.setPrefWidth(260);
                g.add(name, 1, row);
                g.add(scope, 2, row);
                row++;
                checks.put(p.code(), cb);
                scopes.put(p.code(), scope);
            }
            TitledPane tp = new TitledPane(module, g);
            tp.setExpanded(true);
            matrix.getChildren().add(tp);
        });
        f.node().getChildren().add(new Label("Quyền"));
        f.node().getChildren().add(matrix);
        f.size(860, 600).showDialog(existing == null ? "Thêm vai trò" : "Phân quyền – " + existing.roleCode(), "Lưu", () -> {
            List<Grant> grants = new ArrayList<>();
            checks.forEach((code, cb) -> {
                if (cb.isSelected()) grants.add(new Grant(code, scopes.get(code).getValue()));
            });
            RoleRequest r = new RoleRequest(f.str("code"), f.str("name"), f.str("description"), grants);
            return existing == null ? session.api().createRole(r) : session.api().updateRole(existing.id(), r);
        }, r -> reload());
    }
}
