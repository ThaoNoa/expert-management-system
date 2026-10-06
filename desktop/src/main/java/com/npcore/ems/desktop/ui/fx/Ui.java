package com.npcore.ems.desktop.ui.fx;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Khối dựng giao diện dùng chung. */
public final class Ui {
    private Ui() {}

    public static Button button(String text, Runnable action) {
        Button b = new Button(text);
        b.setOnAction(e -> action.run());
        b.setMinWidth(Region.USE_PREF_SIZE);     // không bị cắt chữ khi thanh công cụ chật
        return b;
    }

    public static Button primary(String text, Runnable action) {
        Button b = button(text, action);
        b.getStyleClass().add("primary");
        b.setDefaultButton(false);
        return b;
    }

    public static Button danger(String text, Runnable action) {
        Button b = button(text, action);
        b.getStyleClass().add("danger");
        return b;
    }

    public static Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    public static HBox toolbar(Node... nodes) {
        HBox box = new HBox(8, nodes);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("toolbar");
        return box;
    }

    public static Label title(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("page-title");
        return l;
    }

    public static Label hint(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("hint");
        l.setWrapText(true);
        return l;
    }

    /** Trang chuẩn: tiêu đề + nội dung, có lề. */
    public static VBox page(String title, Node... content) {
        VBox box = new VBox(12);
        box.getChildren().add(title(title));
        box.getChildren().addAll(content);
        box.setPadding(new Insets(16));
        for (Node n : content) VBox.setVgrow(n, Priority.SOMETIMES);
        return box;
    }
}
