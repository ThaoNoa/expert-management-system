package com.npcore.ems.desktop.ui.document;

import com.npcore.ems.desktop.api.Dtos.DocumentDetail;
import com.npcore.ems.desktop.api.Dtos.Link;
import com.npcore.ems.desktop.api.Dtos.Version;
import com.npcore.ems.desktop.api.Session;
import com.npcore.ems.desktop.ui.fx.Async;
import com.npcore.ems.desktop.ui.fx.Dialogs;
import com.npcore.ems.desktop.ui.fx.Fmt;
import com.npcore.ems.desktop.ui.fx.Form;
import com.npcore.ems.desktop.ui.fx.Tables;
import com.npcore.ems.desktop.ui.fx.Ui;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

/** Chi tiết tài liệu: các phiên bản (tải về, xác minh, từ chối, upload bản mới) và liên kết. */
public final class DocumentDetailDialog {

    private DocumentDetailDialog() {}

    public static void open(Session session, UUID documentId, Runnable onChanged) {
        Dialog<ButtonType> d = new Dialog<>();
        Dialogs.init(d, "Tài liệu");
        d.getDialogPane().getButtonTypes().add(Dialogs.CLOSE);
        VBox content = new VBox(10);
        content.setPrefSize(900, 520);
        d.getDialogPane().setContent(content);
        Runnable[] reload = new Runnable[1];
        reload[0] = () -> Async.run(content, () -> session.api().document(documentId), doc -> {
            render(session, content, doc, () -> {
                reload[0].run();
                onChanged.run();
            });
            d.setTitle("Tài liệu – " + doc.title());
        });
        reload[0].run();
        d.show();
    }

    private static void render(Session s, VBox content, DocumentDetail doc, Runnable changed) {
        Label title = Ui.title(doc.title());
        Label meta = Ui.hint(doc.documentTypeName() + (doc.ownerExpertName() == null ? "" : " · Chuyên gia: " + doc.ownerExpertName())
                + " · Tạo " + Fmt.dateTime(doc.createdAt()));

        TableView<Version> versions = Tables.table("Chưa có phiên bản");
        versions.getColumns().addAll(List.of(
                Tables.col("Bản", v -> "v" + v.versionNo(), 50),
                Tables.col("Tên file", Version::fileName, 220),
                Tables.col("Dung lượng", v -> Fmt.size(v.fileSize()), 90),
                Tables.col("Ngày cấp", v -> Fmt.date(v.issuedDate()), 90),
                Tables.col("Hết hạn", v -> Fmt.date(v.expiryDate()), 90),
                Tables.status("Trạng thái", Version::status, 130),
                Tables.col("Upload", v -> Fmt.dateTime(v.uploadedAt()), 120),
                Tables.col("Lý do từ chối", Version::rejectReason, 160)));
        versions.getItems().setAll(doc.versions());
        versions.setPrefHeight(220);

        Button download = Ui.button("Tải về…", () -> {
            Version v = versions.getSelectionModel().getSelectedItem();
            DirectoryChooser dc = new DirectoryChooser();
            dc.setTitle("Lưu vào thư mục");
            File dir = dc.showDialog(content.getScene().getWindow());
            if (dir != null) Async.run(content, () -> s.api().downloadVersion(v.id(), dir.toPath(), v.fileName()),
                    (Path p) -> Dialogs.info("Đã lưu: " + p));
        });
        Button verify = Ui.primary("Xác minh", () -> {
            Version v = versions.getSelectionModel().getSelectedItem();
            if (Dialogs.confirm("Xác nhận tài liệu v" + v.versionNo() + " hợp lệ?")) {
                Async.exec(content, () -> s.api().verifyVersion(v.id()), changed);
            }
        });
        Button reject = Ui.danger("Từ chối", () -> {
            Version v = versions.getSelectionModel().getSelectedItem();
            Dialogs.askText("Từ chối tài liệu", "Lý do từ chối", true).ifPresent(reason ->
                    Async.exec(content, () -> s.api().rejectVersion(v.id(), reason), changed));
        });
        Button newVersion = Ui.button("Upload phiên bản mới…", () -> {
            FileChooser fc = new FileChooser();
            File f = fc.showOpenDialog(content.getScene().getWindow());
            if (f == null) return;
            Form form = new Form().date("issued", "Ngày cấp", false).date("expiry", "Ngày hết hạn", false);
            form.showDialog("Phiên bản mới – " + f.getName(), "Upload", () ->
                    s.api().newDocumentVersion(doc.id(), f.toPath(), form.date("issued"), form.date("expiry")), r -> changed.run());
        });
        download.disableProperty().bind(versions.getSelectionModel().selectedItemProperty().isNull());
        var pending = javafx.beans.binding.Bindings.createBooleanBinding(() -> {
            Version v = versions.getSelectionModel().getSelectedItem();
            return v == null || !"PENDING_VERIFICATION".equals(v.status());
        }, versions.getSelectionModel().selectedItemProperty());
        verify.disableProperty().bind(pending);
        reject.disableProperty().bind(pending);
        var bar = Ui.toolbar(download);
        if (s.has("DOCUMENT_VERIFY")) bar.getChildren().addAll(verify, reject);
        if (s.has("DOCUMENT_MANAGE")) bar.getChildren().add(newVersion);

        TableView<Link> links = Tables.table("Chưa liên kết với đối tượng nào");
        links.getColumns().addAll(List.of(
                Tables.col("Loại đối tượng", Link::objectType, 140),
                Tables.col("Mã đối tượng", Link::objectId, 280),
                Tables.col("Mục đích", Link::purpose, 160),
                Tables.col("Thời điểm", l -> Fmt.dateTime(l.linkedAt()), 130)));
        links.getItems().setAll(doc.links());
        links.setPrefHeight(150);
        Button removeLink = Ui.danger("Bỏ liên kết", () -> {
            Link l = links.getSelectionModel().getSelectedItem();
            if (Dialogs.confirm("Bỏ liên kết với " + l.objectType() + "?")) {
                Async.exec(content, () -> s.api().removeLink(l.id()), changed);
            }
        });
        removeLink.disableProperty().bind(links.getSelectionModel().selectedItemProperty().isNull());

        content.getChildren().setAll(title, meta, new Label("Phiên bản"), bar, versions, new Label("Liên kết"), links);
        if (s.has("DOCUMENT_MANAGE")) content.getChildren().add(Ui.toolbar(removeLink));
        if (!doc.versions().isEmpty()) versions.getSelectionModel().select(0);
    }
}
