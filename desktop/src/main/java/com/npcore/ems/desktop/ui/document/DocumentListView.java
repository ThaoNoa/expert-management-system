package com.npcore.ems.desktop.ui.document;

import com.npcore.ems.desktop.api.Dtos.DocumentSummary;
import com.npcore.ems.desktop.api.Dtos.ExpertSummary;
import com.npcore.ems.desktop.api.Session;
import com.npcore.ems.desktop.ui.fx.Async;
import com.npcore.ems.desktop.ui.fx.Fmt;
import com.npcore.ems.desktop.ui.fx.Form.Option;
import com.npcore.ems.desktop.ui.fx.Lookups;
import com.npcore.ems.desktop.ui.fx.PagedTable;
import com.npcore.ems.desktop.ui.fx.Tables;
import com.npcore.ems.desktop.ui.fx.Ui;
import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Insets;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Kho tài liệu: lọc, upload, xem chi tiết (nhấp đúp). */
public final class DocumentListView extends VBox {

    public DocumentListView(Session session) {
        super(12);
        setPadding(new Insets(16));
        TextField q = new TextField();
        q.setPromptText("Tìm theo tiêu đề");
        ComboBox<Option<String>> type = new ComboBox<>();
        type.setPromptText("Loại tài liệu");
        ComboBox<Option<String>> status = new ComboBox<>();
        status.setPromptText("Trạng thái");
        List<Option<String>> statuses = new ArrayList<>();
        statuses.add(new Option<>(null, "Tất cả trạng thái"));
        for (String c : List.of("PENDING_VERIFICATION", "VERIFIED", "REJECTED", "EXPIRED")) statuses.add(new Option<>(c, Fmt.label(c)));
        status.getItems().setAll(statuses);

        TableView<DocumentSummary> table = Tables.table("Không có tài liệu");
        table.getColumns().addAll(List.of(
                Tables.col("Tiêu đề", DocumentSummary::title, 260),
                Tables.col("Loại", DocumentSummary::documentTypeName, 140),
                Tables.col("Chuyên gia", DocumentSummary::ownerExpertName, 180),
                Tables.col("Bản", d -> d.currentVersion() == null ? "" : "v" + d.currentVersion().versionNo(), 50),
                Tables.col("Hết hạn", d -> d.currentVersion() == null ? "" : Fmt.date(d.currentVersion().expiryDate()), 100),
                Tables.status("Trạng thái", d -> d.currentVersion() == null ? null : d.currentVersion().status(), 140),
                Tables.col("Ngày tạo", d -> Fmt.dateTime(d.createdAt()), 130)));
        PagedTable<DocumentSummary> paged = new PagedTable<>(table, (page, size) -> session.api().documents(null,
                type.getValue() == null ? null : type.getValue().value(),
                status.getValue() == null ? null : status.getValue().value(),
                q.getText().isBlank() ? null : q.getText().trim(), page, size));
        Tables.onOpen(table, d -> DocumentDetailDialog.open(session, d.id(), paged::refresh));
        q.setOnAction(e -> paged.reload());
        type.setOnAction(e -> paged.reload());
        status.setOnAction(e -> paged.reload());

        Lookups[] lookups = new Lookups[1];
        List<ExpertSummary> experts = new ArrayList<>();
        Async.run(null, () -> Lookups.load(session), l -> {
            lookups[0] = l;
            List<Option<String>> types = new ArrayList<>();
            types.add(new Option<>(null, "Tất cả loại"));
            types.addAll(l.documentTypeOptions());
            type.getItems().setAll(types);
        }, e -> {});
        if (session.hasAll("EXPERT_VIEW")) {
            Async.run(null, () -> session.api().experts(null, null, null, null, 0, 500, "fullName,asc").content(),
                    experts::addAll, e -> {});
        }

        var toolbar = Ui.toolbar(q, type, status, Ui.button("Tìm", paged::reload), Ui.spacer());
        if (session.has("DOCUMENT_MANAGE")) {
            toolbar.getChildren().add(Ui.primary("+ Upload tài liệu", () -> {
                if (lookups[0] != null) UploadDialog.open(session, lookups[0], null, experts, r -> paged.reload());
            }));
        }
        VBox.setVgrow(paged, Priority.ALWAYS);
        getChildren().addAll(Ui.title("Tài liệu"), toolbar, paged,
                Ui.hint("Nhấp đúp vào dòng để xem phiên bản, tải về, xác minh hoặc từ chối."));
        paged.reload();
    }
}
