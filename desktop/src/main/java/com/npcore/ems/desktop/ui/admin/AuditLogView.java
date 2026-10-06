package com.npcore.ems.desktop.ui.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.npcore.ems.desktop.api.Dtos.AuditLog;
import com.npcore.ems.desktop.api.Json;
import com.npcore.ems.desktop.api.Session;
import com.npcore.ems.desktop.ui.fx.Dialogs;
import com.npcore.ems.desktop.ui.fx.Fmt;
import com.npcore.ems.desktop.ui.fx.Form;
import com.npcore.ems.desktop.ui.fx.PagedTable;
import com.npcore.ems.desktop.ui.fx.Tables;
import com.npcore.ems.desktop.ui.fx.Ui;
import java.util.List;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Nhật ký hệ thống (FR-14.1): ai, làm gì, lúc nào, từ giá trị nào sang giá trị nào, IP. Chỉ đọc. */
public final class AuditLogView extends VBox {

    public AuditLogView(Session session) {
        super(12);
        setPadding(new Insets(16));
        TextField objectType = new TextField();
        objectType.setPromptText("Loại đối tượng (EXPERT, USER…)");
        TextField objectId = new TextField();
        objectId.setPromptText("Mã đối tượng");
        TextField action = new TextField();
        action.setPromptText("Hành động");
        DatePicker from = new DatePicker();
        from.setPromptText("Từ ngày");
        from.setConverter(Form.dateConverter());
        DatePicker to = new DatePicker();
        to.setPromptText("Đến ngày");
        to.setConverter(Form.dateConverter());

        TableView<AuditLog> table = Tables.table("Không có bản ghi");
        table.getColumns().addAll(List.of(
                Tables.col("Thời điểm", a -> Fmt.dateTime(a.occurredAt()), 140), Tables.col("Người dùng", AuditLog::username, 120),
                Tables.col("Hành động", AuditLog::action, 130), Tables.col("Đối tượng", AuditLog::objectType, 140),
                Tables.col("Mã", AuditLog::objectId, 260), Tables.col("Lý do", AuditLog::reason, 200),
                Tables.col("IP", AuditLog::ipAddress, 110)));
        PagedTable<AuditLog> paged = new PagedTable<>(table, (page, size) -> session.api().auditLogs(
                blank(objectType), blank(objectId), blank(action), from.getValue(), to.getValue(), page, size));
        Tables.onOpen(table, AuditLogView::detail);
        for (TextField t : List.of(objectType, objectId, action)) t.setOnAction(e -> paged.reload());
        VBox.setVgrow(paged, Priority.ALWAYS);
        getChildren().addAll(Ui.title("Nhật ký hệ thống"),
                Ui.toolbar(objectType, objectId, action, from, to, Ui.button("Lọc", paged::reload)), paged,
                Ui.hint("Nhật ký chỉ được ghi thêm, không sửa / xoá được. Nhấp đúp để xem giá trị trước và sau thay đổi."));
        paged.reload();
    }

    private static String blank(TextField t) {
        return t.getText() == null || t.getText().isBlank() ? null : t.getText().trim();
    }

    private static void detail(AuditLog a) {
        Dialog<ButtonType> d = new Dialog<>();
        Dialogs.init(d, "Chi tiết nhật ký #" + a.id());
        d.getDialogPane().getButtonTypes().add(Dialogs.CLOSE);
        GridPane g = new GridPane();
        g.setHgap(12);
        g.setVgap(8);
        g.add(new Label("Trước"), 0, 0);
        g.add(new Label("Sau"), 1, 0);
        g.add(json(a.fromValue()), 0, 1);
        g.add(json(a.toValue()), 1, 1);
        VBox box = new VBox(10, new Label(Fmt.dateTime(a.occurredAt()) + " · " + a.username() + " · " + a.action() + " · "
                + a.objectType() + " " + (a.objectId() == null ? "" : a.objectId())), g);
        d.getDialogPane().setContent(box);
        d.show();
    }

    private static TextArea json(JsonNode n) {
        TextArea t = new TextArea();
        t.setEditable(false);
        t.setPrefSize(420, 360);
        t.getStyleClass().add("mono");
        try {
            t.setText(n == null || n.isNull() || n.isMissingNode() ? "(không có)" : Json.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(n));
        } catch (Exception e) {
            t.setText(String.valueOf(n));
        }
        return t;
    }
}
