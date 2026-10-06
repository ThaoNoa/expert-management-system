package com.npcore.ems.desktop.ui.document;

import com.npcore.ems.desktop.api.Dtos.DocumentSummary;
import com.npcore.ems.desktop.api.Session;
import com.npcore.ems.desktop.ui.fx.Fmt;
import com.npcore.ems.desktop.ui.fx.Form.Option;
import com.npcore.ems.desktop.ui.fx.Lookups;
import com.npcore.ems.desktop.ui.fx.PagedTable;
import com.npcore.ems.desktop.ui.fx.Tables;
import com.npcore.ems.desktop.ui.fx.Ui;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javafx.animation.PauseTransition;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Tài liệu của MỘT chuyên gia (hoặc của tất cả khi expertId == null): tìm theo cụm từ (tiêu đề, loại, tên file,
 * tên / mã chuyên gia – không cần gõ dấu), lọc loại / trạng thái, tải lên, nhấp đúp để xem phiên bản / tải về / xác minh.
 * Dùng ở màn hình "Tài liệu chuyên gia" và tab "Tài liệu" trong hồ sơ chuyên gia.
 */
public final class ExpertDocumentsPane extends VBox {

    private final Session session;
    private final Lookups lookups;
    private final TextField q = new TextField();
    private final ComboBox<Option<String>> type = new ComboBox<>();
    private final ComboBox<Option<String>> status = new ComboBox<>();
    private final PagedTable<DocumentSummary> paged;
    private final TableColumn<DocumentSummary, ?> expertCol;
    private final Button upload;
    private UUID expertId;
    private String expertLabel;
    private boolean editable = true;

    public ExpertDocumentsPane(Session session, Lookups lookups, UUID expertId) {
        super(10);
        this.session = session;
        this.lookups = lookups;
        this.expertId = expertId;

        q.setPromptText("Tìm theo cụm từ: tiêu đề, loại, tên file, tên chuyên gia…  (không cần gõ dấu)");
        q.setPrefWidth(360);
        List<Option<String>> types = new ArrayList<>();
        types.add(new Option<>(null, "Tất cả loại"));
        types.addAll(lookups.documentTypeOptions());
        type.getItems().setAll(types);
        type.setPromptText("Loại tài liệu");
        List<Option<String>> statuses = new ArrayList<>();
        statuses.add(new Option<>(null, "Tất cả trạng thái"));
        for (String c : List.of("PENDING_VERIFICATION", "VERIFIED", "REJECTED", "EXPIRED")) statuses.add(new Option<>(c, Fmt.label(c)));
        status.getItems().setAll(statuses);
        status.setPromptText("Trạng thái");

        TableView<DocumentSummary> table = Tables.table("Chưa có tài liệu");
        expertCol = Tables.col("Chuyên gia", DocumentSummary::ownerExpertName, 170);
        table.getColumns().addAll(List.of(
                Tables.col("Tiêu đề", DocumentSummary::title, 260),
                Tables.col("Loại", DocumentSummary::documentTypeName, 130),
                expertCol,
                Tables.col("Tên file", d -> d.currentVersion() == null ? "" : d.currentVersion().fileName(), 170),
                Tables.col("Bản", d -> d.currentVersion() == null ? "" : "v" + d.currentVersion().versionNo(), 50),
                Tables.col("Hết hạn", d -> d.currentVersion() == null ? "" : Fmt.date(d.currentVersion().expiryDate()), 95),
                Tables.status("Trạng thái", d -> d.currentVersion() == null ? null : d.currentVersion().status(), 130),
                Tables.col("Ngày tải lên", d -> Fmt.dateTime(d.createdAt()), 125)));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.getColumns().get(0).setPrefWidth(320);                 // tiêu đề rộng nhất
        table.getColumns().get(4).setMinWidth(46);                   // "Bản"
        paged = new PagedTable<>(table, (page, size) -> session.api().documents(this.expertId, value(type), value(status),
                q.getText() == null || q.getText().isBlank() ? null : q.getText().trim(), page, size));
        Tables.onOpen(table, d -> DocumentDetailDialog.open(session, d.id(), paged::refresh));

        // tìm ngay khi gõ (đợi 0,4 giây sau phím cuối)
        PauseTransition debounce = new PauseTransition(Duration.millis(400));
        debounce.setOnFinished(e -> paged.reload());
        q.textProperty().addListener((o, a, b) -> debounce.playFromStart());
        q.setOnAction(e -> paged.reload());
        type.setOnAction(e -> paged.reload());
        status.setOnAction(e -> paged.reload());

        upload = Ui.primary("+ Tải tài liệu lên", () -> UploadDialog.open(session, lookups, this.expertId, expertLabel, List.of(),
                r -> paged.reload()));
        var toolbar = Ui.toolbar(q, type, status, Ui.spacer());
        if (session.has("DOCUMENT_MANAGE")) toolbar.getChildren().add(upload);
        VBox.setVgrow(paged, Priority.ALWAYS);
        getChildren().addAll(toolbar, paged,
                Ui.hint("Nhấp đúp vào tài liệu để xem các phiên bản, tải về, tải phiên bản mới, xác minh hoặc từ chối."));
        refreshState();
        paged.reload();
    }

    /** Đổi chuyên gia đang xem (null = tất cả). */
    public void showExpert(UUID id) {
        showExpert(id, null);
    }

    public void showExpert(UUID id, String label) {
        this.expertId = id;
        this.expertLabel = label;
        refreshState();
        paged.reload();
    }

    /** Khoá nút tải lên (VD hồ sơ đang chờ GĐCN phê duyệt). */
    public void setEditable(boolean editable) {
        this.editable = editable;
        refreshState();
    }

    public void reload() {
        paged.reload();
    }

    private void refreshState() {
        expertCol.setVisible(expertId == null);
        upload.setDisable(expertId == null || !editable);
        upload.setText(expertId == null ? "Chọn chuyên gia để tải lên" : "+ Tải tài liệu lên");
    }

    private static String value(ComboBox<Option<String>> c) {
        return c.getValue() == null ? null : c.getValue().value();
    }

    Lookups lookups() {
        return lookups;
    }
}
