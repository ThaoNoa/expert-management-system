package com.npcore.ems.desktop.ui.fx;

import java.util.function.Consumer;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.MouseButton;

/** Tiện ích dựng cột TableView. */
public final class Tables {
    private Tables() {}

    public static <T> TableColumn<T, String> col(String title, Function<T, Object> value, double width) {
        TableColumn<T, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cd -> new ReadOnlyStringWrapper(Fmt.text(value.apply(cd.getValue()))));
        c.setPrefWidth(width);
        return c;
    }

    /** Cột trạng thái hiển thị dạng nhãn màu (ok / warn / danger / muted). */
    public static <T> TableColumn<T, String> status(String title, Function<T, String> code, double width) {
        TableColumn<T, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(code.apply(cd.getValue())));
        c.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || Fmt.label(item).isEmpty()) {
                    setGraphic(null);
                    return;
                }
                setGraphic(tag(item));
                setText(null);
            }
        });
        c.setPrefWidth(width);
        return c;
    }

    public static Label tag(String code) {
        Label l = new Label(Fmt.label(code));
        l.getStyleClass().addAll("tag", "tag-" + Fmt.tone(code));
        return l;
    }

    /** Nhấp đúp một dòng để mở. */
    public static <T> void onOpen(TableView<T> table, Consumer<T> open) {
        table.setRowFactory(tv -> {
            TableRow<T> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2 && !row.isEmpty()) {
                    open.accept(row.getItem());
                }
            });
            return row;
        });
    }

    public static <T> TableView<T> table(String emptyText) {
        TableView<T> t = new TableView<>();
        t.setPlaceholder(new Label(emptyText));
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        return t;
    }
}
