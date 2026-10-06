package com.npcore.ems.desktop.ui.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.npcore.ems.desktop.api.ApiException;
import com.npcore.ems.desktop.api.Dtos.Setting;
import com.npcore.ems.desktop.api.Json;
import com.npcore.ems.desktop.api.Session;
import com.npcore.ems.desktop.ui.fx.Async;
import com.npcore.ems.desktop.ui.fx.Form;
import com.npcore.ems.desktop.ui.fx.Tables;
import com.npcore.ems.desktop.ui.fx.Ui;
import java.math.BigDecimal;
import java.util.List;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Cấu hình hệ thống (FR-17): ngưỡng cảnh báo hết hạn, chính sách mật khẩu... sửa được, không hard-code. */
public final class SettingsView extends VBox {

    public SettingsView(Session session) {
        super(12);
        setPadding(new Insets(16));
        TableView<Setting> table = Tables.table("Không có cấu hình");
        table.getColumns().addAll(List.of(
                Tables.col("Nhóm", Setting::category, 110), Tables.col("Khoá", Setting::key, 260),
                Tables.col("Giá trị", s -> s.value() == null ? "" : s.value().isTextual() ? s.value().asText() : s.value().toString(), 120),
                Tables.col("Kiểu", Setting::valueType, 80), Tables.col("Mô tả", Setting::description, 320)));
        Runnable reload = () -> Async.run(table, () -> session.api().settings(), rows -> table.getItems().setAll(rows));
        Runnable edit = () -> {
            Setting s = table.getSelectionModel().getSelectedItem();
            if (s == null) return;
            Form f = new Form();
            if ("BOOLEAN".equals(s.valueType())) f.check("v", s.key()).set("v", s.value().asBoolean());
            else f.text("v", s.key(), true).set("v", s.value().isTextual() ? s.value().asText() : s.value().toString());
            f.note(s.description() == null ? "" : s.description());
            f.showDialog("Sửa cấu hình", "Lưu", () -> session.api().updateSetting(s.key(), toJson(s.valueType(), f)), r -> reload.run());
        };
        Button editBtn = Ui.button("Sửa giá trị", edit);
        editBtn.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        Tables.onOpen(table, s -> edit.run());
        VBox.setVgrow(table, Priority.ALWAYS);
        getChildren().addAll(Ui.title("Cấu hình hệ thống"), Ui.toolbar(Ui.button("Tải lại", reload), Ui.spacer(), editBtn), table);
        reload.run();
    }

    private static JsonNode toJson(String type, Form f) {
        try {
            return switch (type) {
                case "INT" -> IntNode.valueOf(Integer.parseInt(f.str("v")));
                case "DECIMAL" -> DecimalNode.valueOf(new BigDecimal(f.str("v").replace(',', '.')));
                case "BOOLEAN" -> BooleanNode.valueOf(f.bool("v"));
                case "JSON" -> Json.MAPPER.readTree(f.str("v"));
                default -> TextNode.valueOf(f.str("v"));
            };
        } catch (Exception e) {
            throw new ApiException(400, "VALIDATION_ERROR", "Giá trị không đúng kiểu " + type, null);
        }
    }
}
