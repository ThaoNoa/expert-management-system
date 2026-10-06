package com.npcore.ems.desktop.ui.document;

import com.npcore.ems.desktop.api.Dtos.ExpertSummary;
import com.npcore.ems.desktop.api.Session;
import com.npcore.ems.desktop.ui.fx.Async;
import com.npcore.ems.desktop.ui.fx.Fmt;
import com.npcore.ems.desktop.ui.fx.Lookups;
import com.npcore.ems.desktop.ui.fx.Tables;
import com.npcore.ems.desktop.ui.fx.Ui;
import java.util.UUID;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Tài liệu chuyên gia: trái là danh sách chuyên gia (tìm theo tên / mã, không cần dấu), phải là tài liệu của
 * chuyên gia đang chọn – tải lên, tìm theo cụm từ, xem / xác minh. "Tất cả chuyên gia" để tìm trong toàn bộ kho.
 * Tài khoản chuyên gia (chỉ thấy hồ sơ của mình) chỉ thấy phần bên phải với tài liệu của chính mình.
 */
public final class DocumentListView extends BorderPane {

    private final Session session;
    private final Label heading = Ui.title("Tài liệu chuyên gia");
    private final Label subHeading = Ui.hint("");

    public DocumentListView(Session session) {
        this.session = session;
        setPadding(new Insets(16));
        setTop(new VBox(2, heading, subHeading));
        setCenter(new Label("Đang tải…"));
        Async.run(this, () -> Lookups.load(session), this::build);
    }

    private void build(Lookups lookups) {
        boolean all = session.hasAll("EXPERT_VIEW");
        UUID ownExpert = session.me().expertId();
        ExpertDocumentsPane docs = new ExpertDocumentsPane(session, lookups, all ? null : ownExpert);
        BorderPane.setMargin(docs, new Insets(12, 0, 0, 0));
        if (!all) {
            subHeading.setText("Tài liệu trong hồ sơ của bạn");
            setCenter(docs);
            return;
        }
        subHeading.setText("Chọn một chuyên gia ở bên trái để xem và tải tài liệu lên, hoặc tìm trong toàn bộ kho.");

        // ---- danh sách chuyên gia
        TextField find = new TextField();
        find.setPromptText("Tìm chuyên gia (tên, mã)…");
        ListView<ExpertSummary> experts = new ListView<>();
        experts.setCellFactory(v -> new ExpertCell());
        experts.setPlaceholder(new Label("Không có chuyên gia phù hợp"));
        Button allButton = Ui.button("Tất cả chuyên gia", () -> experts.getSelectionModel().clearSelection());
        allButton.setMaxWidth(Double.MAX_VALUE);
        Runnable loadExperts = () -> Async.run(experts, () -> session.api().experts(
                        find.getText() == null || find.getText().isBlank() ? null : find.getText().trim(),
                        null, null, null, 0, 500, "fullName,asc").content(),
                rows -> experts.getItems().setAll(rows));
        PauseTransition debounce = new PauseTransition(Duration.millis(350));
        debounce.setOnFinished(e -> loadExperts.run());
        find.textProperty().addListener((o, a, b) -> debounce.playFromStart());

        experts.getSelectionModel().selectedItemProperty().addListener((o, a, e) -> {
            if (e == null) {
                heading.setText("Tài liệu chuyên gia");
                subHeading.setText("Đang xem: tất cả chuyên gia. Chọn một chuyên gia để tải tài liệu lên.");
                docs.setEditable(true);
                docs.showExpert(null);
            } else {
                heading.setText(e.fullName() + "  ·  " + e.expertCode());
                boolean locked = "SUBMITTED".equals(e.status()) || "REVIEWED".equals(e.status());
                subHeading.setText(Fmt.label(e.expertType()) + " · " + Fmt.label(e.status())
                        + (locked ? " – hồ sơ đã trình (thẩm tra / phê duyệt) nên tạm khoá tải lên" : ""));
                docs.setEditable(!locked);
                docs.showExpert(e.id(), e.expertCode() + " · " + e.fullName());
            }
        });

        VBox left = new VBox(8, find, allButton, experts);
        VBox.setVgrow(experts, Priority.ALWAYS);
        left.setPadding(new Insets(0, 10, 0, 0));
        left.setMinWidth(240);
        SplitPane split = new SplitPane(left, docs);
        split.setDividerPositions(0.24);
        SplitPane.setResizableWithParent(left, false);
        BorderPane.setMargin(split, new Insets(12, 0, 0, 0));
        setCenter(split);
        loadExperts.run();
    }

    /** Ô chuyên gia: họ tên (đậm) + mã · trạng thái. */
    private static final class ExpertCell extends ListCell<ExpertSummary> {
        @Override
        protected void updateItem(ExpertSummary e, boolean empty) {
            super.updateItem(e, empty);
            if (empty || e == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            Label name = new Label(e.fullName());
            name.getStyleClass().add("cell-title");
            Label code = new Label(e.expertCode());
            code.getStyleClass().add("hint");
            Region gap = new Region();
            HBox.setHgrow(gap, Priority.ALWAYS);
            HBox line2 = new HBox(6, code, gap, Tables.tag(e.status()));
            line2.setAlignment(Pos.CENTER_LEFT);
            VBox box = new VBox(2, name, line2);
            setText(null);
            setGraphic(box);
        }
    }
}
