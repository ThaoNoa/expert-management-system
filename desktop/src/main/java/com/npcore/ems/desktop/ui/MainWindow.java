package com.npcore.ems.desktop.ui;

import com.npcore.ems.desktop.api.Session;
import com.npcore.ems.desktop.ui.admin.AuditLogView;
import com.npcore.ems.desktop.ui.admin.RolesView;
import com.npcore.ems.desktop.ui.admin.SettingsView;
import com.npcore.ems.desktop.ui.admin.UsersView;
import com.npcore.ems.desktop.ui.document.DocumentListView;
import com.npcore.ems.desktop.ui.expert.ExpertDetailView;
import com.npcore.ems.desktop.ui.expert.ExpertImportView;
import com.npcore.ems.desktop.ui.expert.ExpertListView;
import com.npcore.ems.desktop.ui.fx.Async;
import com.npcore.ems.desktop.ui.fx.Dialogs;
import com.npcore.ems.desktop.ui.fx.Form;
import com.npcore.ems.desktop.ui.master.CatalogViews;
import com.npcore.ems.desktop.ui.master.CodeSetsView;
import com.npcore.ems.desktop.ui.master.StandardsView;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/** Cửa sổ chính: thanh menu trái (lọc theo quyền), vùng làm việc dạng tab, thông tin người dùng. */
public final class MainWindow extends BorderPane implements Navigator {

    /** Mục trên cây điều hướng. view == null → nhóm. */
    private record Nav(String key, String title, Supplier<Node> view) {
        @Override
        public String toString() {
            return title;
        }
    }

    private final Session session;
    private final TabPane tabs = new TabPane();

    public MainWindow(Session session, Consumer<Session> onLogout) {
        this.session = session;
        getStyleClass().add("main");
        setTop(header(onLogout));
        setLeft(navigation());
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        setCenter(tabs);
        if (staff()) {
            open("dashboard", "Tổng quan", () -> new DashboardView(session, this));
        } else if (session.me().expertId() != null) {               // chuyên gia: vào thẳng hồ sơ của mình
            open("me", "Hồ sơ của tôi", () -> new ExpertDetailView(session, this, null));
        }
    }

    private Node header(Consumer<Session> onLogout) {
        Label app = new Label("EMS · Quản lý chuyên gia");
        app.getStyleClass().add("app-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        MenuButton user = new MenuButton(session.me().fullName());
        user.getStyleClass().add("user-menu");
        MenuItem info = new MenuItem(session.me().username() + " · " + String.join(", ", session.me().roles()));
        info.setDisable(true);
        MenuItem changePassword = new MenuItem("Đổi mật khẩu…");
        changePassword.setOnAction(e -> changePassword());
        MenuItem logout = new MenuItem("Đăng xuất");
        logout.setOnAction(e -> {
            if (Dialogs.confirm("Đăng xuất khỏi EMS?")) onLogout.accept(session);
        });
        user.getItems().addAll(info, new SeparatorMenuItem(), changePassword, logout);
        HBox bar = new HBox(12, app, spacer, user);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10, 16, 10, 16));
        bar.getStyleClass().add("header");
        return bar;
    }

    /** Nhân sự nội bộ xem được toàn bộ chuyên gia (khác tài khoản chuyên gia – chỉ thấy hồ sơ của mình). */
    private boolean staff() {
        return session.hasAll("EXPERT_VIEW");
    }

    private Node navigation() {
        TreeItem<Nav> root = new TreeItem<>(new Nav("root", "EMS", null));
        if (staff()) root.getChildren().add(leaf("dashboard", "Tổng quan", () -> new DashboardView(session, this)));

        TreeItem<Nav> experts = group("Chuyên gia");
        if (session.hasAll("EXPERT_VIEW")) {
            experts.getChildren().add(leaf("experts", "Danh sách chuyên gia", () -> new ExpertListView(session, this)));
            if (session.has("EXPERT_REVIEW")) {
                experts.getChildren().add(leaf("experts-review", "Chờ thẩm tra",
                        () -> new ExpertListView(session, this, "SUBMITTED", "Hồ sơ chờ Chuyên gia trưởng thẩm tra")));
            }
            if (session.has("EXPERT_APPROVE")) {
                experts.getChildren().add(leaf("experts-pending", "Chờ phê duyệt",
                        () -> new ExpertListView(session, this, "REVIEWED", "Hồ sơ đã thẩm tra, chờ GĐCN phê duyệt")));
            }
        }
        if (session.has("EXPERT_CREATE") && session.has("EXPERT_COMPETENCY_EDIT")) {
            experts.getChildren().add(leaf("expert-import", "Import hồ sơ", () -> new ExpertImportView(session)));
        }
        if (session.me().expertId() != null) {
            experts.getChildren().add(leaf("me", "Hồ sơ của tôi", () -> new ExpertDetailView(session, this, null)));
        }
        addIfAny(root, experts);

        if (staff() && session.hasAny("DOCUMENT_MANAGE", "DOCUMENT_VERIFY")) {
            root.getChildren().add(leaf("documents", "Tài liệu chuyên gia", () -> new DocumentListView(session)));
        }

        TreeItem<Nav> master = group("Danh mục");
        master.getChildren().addAll(
                leaf("departments", "Phòng ban", () -> CatalogViews.departments(session)),
                leaf("assessment-roles", "Vai trò đánh giá", () -> CatalogViews.assessmentRoles(session)),
                leaf("schemes", "Scheme", () -> CatalogViews.schemes(session)),
                leaf("standards", "Tiêu chuẩn", () -> new StandardsView(session)),
                leaf("code-sets", "Bộ mã & Code", () -> new CodeSetsView(session)),
                leaf("industries", "Ngành", () -> CatalogViews.industries(session)),
                leaf("activities", "Hoạt động", () -> CatalogViews.activities(session)),
                leaf("locations", "Địa điểm", () -> CatalogViews.locations(session)),
                leaf("education-fields", "Lĩnh vực đào tạo", () -> CatalogViews.educationFields(session)));
        if (staff() || session.has("MASTER_DATA_MANAGE")) root.getChildren().add(master);   // chuyên gia không thấy danh mục

        TreeItem<Nav> admin = group("Quản trị");
        if (session.has("USER_MANAGE")) {
            admin.getChildren().add(leaf("users", "Người dùng", () -> new UsersView(session)));
            admin.getChildren().add(leaf("roles", "Vai trò & quyền", () -> new RolesView(session)));
        }
        if (session.has("SETTING_MANAGE")) {
            admin.getChildren().add(leaf("settings", "Cấu hình", () -> new SettingsView(session)));
        }
        if (session.has("AUDIT_LOG_VIEW")) {
            admin.getChildren().add(leaf("audit", "Nhật ký hệ thống", () -> new AuditLogView(session)));
        }
        addIfAny(root, admin);

        TreeView<Nav> tree = new TreeView<>(root);
        tree.setShowRoot(false);
        tree.getStyleClass().add("nav");
        tree.setPrefWidth(230);
        tree.getSelectionModel().selectedItemProperty().addListener((obs, old, item) -> {
            if (item != null && item.getValue().view() != null) {
                Nav n = item.getValue();
                open(n.key(), n.title(), n.view());
            }
        });
        return tree;
    }

    private static TreeItem<Nav> group(String title) {
        TreeItem<Nav> g = new TreeItem<>(new Nav(title, title, null));
        g.setExpanded(true);
        return g;
    }

    private static TreeItem<Nav> leaf(String key, String title, Supplier<Node> view) {
        return new TreeItem<>(new Nav(key, title, view));
    }

    private static void addIfAny(TreeItem<Nav> root, TreeItem<Nav> group) {
        if (!group.getChildren().isEmpty()) root.getChildren().add(group);
    }

    @Override
    public void open(String key, String title, Supplier<Node> view) {
        for (Tab t : tabs.getTabs()) {
            if (key.equals(t.getUserData())) {
                tabs.getSelectionModel().select(t);
                return;
            }
        }
        Tab tab = new Tab(title, view.get());
        tab.setUserData(key);
        tabs.getTabs().add(tab);
        tabs.getSelectionModel().select(tab);
    }

    private void changePassword() {
        Form f = new Form().password("current", "Mật khẩu hiện tại", true).password("next", "Mật khẩu mới", true)
                .password("confirm", "Nhập lại mật khẩu mới", true)
                .note("Tối thiểu 8 ký tự, gồm chữ, số và ký tự đặc biệt.");
        f.showDialog("Đổi mật khẩu", "Đổi mật khẩu", () -> {
            if (!f.str("next").equals(f.str("confirm"))) {
                throw new com.npcore.ems.desktop.api.ApiException(400, "VALIDATION_ERROR", "Mật khẩu nhập lại không khớp", null);
            }
            session.api().changePassword(f.str("current"), f.str("next"));
            return Boolean.TRUE;
        }, ok -> Dialogs.info("Đã đổi mật khẩu. Các phiên đăng nhập khác sẽ phải đăng nhập lại."));
    }

    /** Dùng khi cần chạy tác vụ nền gắn với cửa sổ chính. */
    void busy(Runnable r) {
        Async.exec(this, r::run, () -> {});
    }
}
