package com.npcore.ems.desktop.ui.fx;

import com.npcore.ems.desktop.api.ApiException;
import java.util.Optional;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

public final class Dialogs {

    /** Nút đóng/huỷ có nhãn tiếng Việt (ButtonType mặc định của JavaFX hiển thị tiếng Anh). */
    public static final ButtonType CANCEL = new ButtonType("Huỷ", ButtonBar.ButtonData.CANCEL_CLOSE);
    public static final ButtonType CLOSE = new ButtonType("Đóng", ButtonBar.ButtonData.CANCEL_CLOSE);

    private static Window owner;

    private Dialogs() {}

    public static void setOwner(Window w) {
        owner = w;
    }

    public static void error(ApiException e) {
        Alert a = new Alert(Alert.AlertType.ERROR, "", CLOSE);
        init(a, e.status() == 0 ? "Lỗi kết nối" : "Không thực hiện được");
        a.setHeaderText(null);
        a.setContentText(e.userMessage());
        a.showAndWait();
    }

    public static void info(String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, "", CLOSE);
        init(a, "Thông báo");
        a.setHeaderText(null);
        a.setContentText(message);
        a.showAndWait();
    }

    public static boolean confirm(String message) {
        ButtonType yes = new ButtonType("Đồng ý", ButtonBar.ButtonData.OK_DONE);
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, message, yes, CANCEL);
        init(a, "Xác nhận");
        a.setHeaderText(null);
        return a.showAndWait().filter(b -> b == yes).isPresent();
    }

    /** Hỏi một đoạn văn bản (lý do, ghi chú). required = bắt buộc nhập. */
    public static Optional<String> askText(String title, String prompt, boolean required) {
        Dialog<String> d = new Dialog<>();
        init(d, title);
        TextArea area = new TextArea();
        area.setPrefRowCount(4);
        area.setWrapText(true);
        Label label = new Label(prompt + (required ? " *" : ""));
        VBox box = new VBox(8, label, area);
        box.setPrefWidth(420);
        d.getDialogPane().setContent(box);
        ButtonType ok = new ButtonType("Đồng ý", ButtonBar.ButtonData.OK_DONE);
        d.getDialogPane().getButtonTypes().addAll(ok, CANCEL);
        d.getDialogPane().lookupButton(ok).disableProperty()
                .bind(area.textProperty().isEmpty().and(new javafx.beans.property.SimpleBooleanProperty(required)));
        d.setResultConverter(b -> b == ok ? area.getText().trim() : null);
        area.requestFocus();
        return d.showAndWait();
    }

    public static void init(Dialog<?> d, String title) {
        d.setTitle(title);
        if (owner != null) d.initOwner(owner);
        d.getDialogPane().getStylesheets().add(Dialogs.class.getResource("/com/npcore/ems/desktop/app.css").toExternalForm());
    }
}
