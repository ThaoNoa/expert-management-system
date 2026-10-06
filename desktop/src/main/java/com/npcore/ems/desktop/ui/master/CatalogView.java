package com.npcore.ems.desktop.ui.master;

import com.npcore.ems.desktop.api.Session;
import com.npcore.ems.desktop.ui.fx.Async;
import com.npcore.ems.desktop.ui.fx.Form;
import com.npcore.ems.desktop.ui.fx.Tables;
import com.npcore.ems.desktop.ui.fx.Ui;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Function;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Màn hình danh mục dùng chung: bảng + lọc nhanh + Thêm / Sửa (ghi cần quyền MASTER_DATA_MANAGE). */
public final class CatalogView<T> extends VBox {

    /** Mô tả một danh mục. */
    public record Spec<T>(String title, String resource, String noun, Callable<List<T>> loader,
                          List<TableColumn<T, String>> columns, Function<T, UUID> id, Function<T, String> searchText,
                          Function<T, Form> form, Function<Form, Map<String, Object>> body) {}

    private final TableView<T> table;
    private List<T> all = List.of();

    public CatalogView(Session session, Spec<T> spec) {
        super(12);
        setPadding(new Insets(16));
        table = Tables.table("Chưa có dữ liệu");
        table.getColumns().addAll(spec.columns());
        TextField filter = new TextField();
        filter.setPromptText("Lọc nhanh");
        filter.textProperty().addListener((o, a, text) -> applyFilter(spec, text));
        Runnable reload = () -> Async.run(table, spec.loader(), rows -> {
            all = rows;
            applyFilter(spec, filter.getText());
        });

        var toolbar = Ui.toolbar(filter, Ui.button("Tải lại", reload), Ui.spacer());
        if (session.has("MASTER_DATA_MANAGE")) {
            Button add = Ui.primary("+ Thêm " + spec.noun(), () -> {
                Form f = spec.form().apply(null);
                f.showDialog("Thêm " + spec.noun(), "Lưu", () -> session.api().create(spec.resource(), spec.body().apply(f)),
                        r -> reload.run());
            });
            Button edit = Ui.button("Sửa", () -> edit(session, spec, reload));
            edit.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
            Tables.onOpen(table, item -> edit(session, spec, reload));
            toolbar.getChildren().addAll(edit, add);
        }
        VBox.setVgrow(table, Priority.ALWAYS);
        getChildren().addAll(Ui.title(spec.title()), toolbar, table);
        reload.run();
    }

    private void edit(Session session, Spec<T> spec, Runnable reload) {
        T sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        Form f = spec.form().apply(sel);
        f.showDialog("Sửa " + spec.noun(), "Lưu",
                () -> session.api().update(spec.resource(), spec.id().apply(sel), spec.body().apply(f)), r -> reload.run());
    }

    private void applyFilter(Spec<T> spec, String text) {
        if (text == null || text.isBlank()) {
            table.getItems().setAll(all);
            return;
        }
        String needle = text.trim().toLowerCase();
        table.getItems().setAll(all.stream().filter(t -> spec.searchText().apply(t).toLowerCase().contains(needle)).toList());
    }
}
