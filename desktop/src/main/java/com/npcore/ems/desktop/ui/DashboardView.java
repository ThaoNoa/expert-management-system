package com.npcore.ems.desktop.ui;

import com.npcore.ems.desktop.api.Session;
import com.npcore.ems.desktop.ui.expert.ExpertDetailView;
import com.npcore.ems.desktop.ui.expert.ExpertListView;
import com.npcore.ems.desktop.ui.fx.Async;
import com.npcore.ems.desktop.ui.fx.Ui;
import java.util.LinkedHashMap;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

/** Màn hình tổng quan: số chuyên gia theo trạng thái, tài liệu chờ xác minh, lối tắt. */
public final class DashboardView extends VBox {

    public DashboardView(Session session, Navigator nav) {
        super(16);
        setPadding(new Insets(20));
        Label hello = Ui.title("Xin chào, " + session.me().fullName());
        getChildren().add(hello);

        FlowPane cards = new FlowPane(16, 16);
        getChildren().add(cards);

        if (session.hasAll("EXPERT_VIEW")) {
            Map<String, String> statuses = new LinkedHashMap<>();
            statuses.put("ACTIVE", "Chuyên gia đang hoạt động");
            statuses.put("DRAFT", "Hồ sơ nháp");
            statuses.put("SUSPENDED", "Đang tạm dừng");
            statuses.put("INACTIVE", "Ngừng hoạt động");
            statuses.forEach((status, label) -> {
                VBox card = card(label, "…", status.equals("SUSPENDED") ? "warn" : status.equals("ACTIVE") ? "ok" : "muted");
                cards.getChildren().add(card);
                Async.run(card, () -> session.api().experts(null, null, null, status, 0, 1, null).totalElements(),
                        n -> ((Label) card.getChildren().get(1)).setText(String.valueOf(n)), e -> {});
            });
        }
        if (session.has("DOCUMENT_VERIFY")) {
            VBox card = card("Tài liệu chờ xác minh", "…", "warn");
            cards.getChildren().add(card);
            Async.run(card, () -> session.api().documents(null, null, "PENDING_VERIFICATION", null, 0, 1).totalElements(),
                    n -> ((Label) card.getChildren().get(1)).setText(String.valueOf(n)), e -> {});
        }

        FlowPane actions = new FlowPane(8, 8);
        if (session.hasAll("EXPERT_VIEW")) {
            actions.getChildren().add(Ui.button("Mở danh sách chuyên gia",
                    () -> nav.open("experts", "Danh sách chuyên gia", () -> new ExpertListView(session, nav))));
        }
        if (session.me().expertId() != null) {
            actions.getChildren().add(Ui.button("Hồ sơ của tôi",
                    () -> nav.open("me", "Hồ sơ của tôi", () -> new ExpertDetailView(session, nav, null))));
        }
        if (!actions.getChildren().isEmpty()) {
            getChildren().addAll(new Label("Lối tắt"), actions);
        }
        getChildren().add(Ui.hint("Chọn chức năng ở thanh bên trái. Mỗi chức năng mở trong một tab; nhấp đúp vào dòng để xem chi tiết."));
    }

    private static VBox card(String label, String value, String tone) {
        Label v = new Label(value);
        v.getStyleClass().add("stat-value");
        Label l = new Label(label);
        l.getStyleClass().add("stat-label");
        VBox box = new VBox(4, l, v);
        box.getStyleClass().addAll("stat-card", "stat-" + tone);
        box.setPrefWidth(220);
        return box;
    }
}
