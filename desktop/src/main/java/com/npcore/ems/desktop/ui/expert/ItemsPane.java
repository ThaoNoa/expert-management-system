package com.npcore.ems.desktop.ui.expert;

import com.npcore.ems.desktop.ui.fx.Async;
import com.npcore.ems.desktop.ui.fx.Dialogs;
import com.npcore.ems.desktop.ui.fx.Form;
import com.npcore.ems.desktop.ui.fx.Tables;
import com.npcore.ems.desktop.ui.fx.Ui;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Function;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Danh sách con của hồ sơ (học vấn, kinh nghiệm...): bảng + Thêm / Sửa / Xoá qua hộp thoại form. */
final class ItemsPane<T> extends VBox {

    interface FormFactory<T> {
        /** Tạo form (đã điền sẵn nếu existing != null). Chạy trên UI thread. */
        Form build(T existing);
    }

    interface Submit<T> {
        void save(Form form, T existing) throws Exception;
    }

    interface Remove<T> {
        void delete(T item) throws Exception;
    }

    private final TableView<T> table;
    private final Callable<List<T>> loader;

    ItemsPane(String noun, List<TableColumn<T, String>> columns, Callable<List<T>> loader, boolean canEdit,
              FormFactory<T> forms, Submit<T> submit, Remove<T> remove, Function<T, String> describe) {
        super(10);
        setPadding(new Insets(12));
        this.loader = loader;
        table = Tables.table("Chưa có " + noun.toLowerCase());
        table.getColumns().addAll(columns);
        VBox.setVgrow(table, Priority.ALWAYS);

        if (canEdit) {
            Button add = Ui.primary("+ Thêm " + noun.toLowerCase(), () -> {
                Form f = forms.build(null);
                f.showDialog("Thêm " + noun.toLowerCase(), "Lưu", () -> {
                    submit.save(f, null);
                    return Boolean.TRUE;
                }, ok -> reload());
            });
            Button edit = Ui.button("Sửa", () -> edit(noun, forms, submit));
            Button del = Ui.danger("Xoá", () -> {
                T sel = table.getSelectionModel().getSelectedItem();
                if (sel == null || !Dialogs.confirm("Xoá " + noun.toLowerCase() + " \"" + describe.apply(sel) + "\"?")) return;
                Async.exec(this, () -> remove.delete(sel), this::reload);
            });
            edit.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
            del.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
            Tables.onOpen(table, item -> edit(noun, forms, submit));
            getChildren().add(Ui.toolbar(add, edit, del));
        }
        getChildren().add(table);
    }

    private void edit(String noun, FormFactory<T> forms, Submit<T> submit) {
        T sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        Form f = forms.build(sel);
        f.showDialog("Sửa " + noun.toLowerCase(), "Lưu", () -> {
            submit.save(f, sel);
            return Boolean.TRUE;
        }, ok -> reload());
    }

    void reload() {
        Async.run(this, loader, items -> table.getItems().setAll(items));
    }
}
