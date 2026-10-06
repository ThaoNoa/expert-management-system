package com.npcore.ems.desktop.ui.fx;

import com.npcore.ems.desktop.api.Page;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * Bảng phân trang phía máy chủ (NFR-P-01/02): chỉ tải một trang dữ liệu mỗi lần.
 * Loader nhận (page, size) và trả Page; chạy ngoài UI thread.
 */
public final class PagedTable<T> extends BorderPane {

    @FunctionalInterface
    public interface Loader<T> {
        Page<T> load(int page, int size) throws Exception;
    }

    private final TableView<T> table;
    private final Loader<T> loader;
    private final Label info = new Label();
    private final Button prev = new Button("‹ Trước");
    private final Button next = new Button("Sau ›");
    private final ComboBox<Integer> sizeBox = new ComboBox<>();
    private int page;
    private int totalPages;

    public PagedTable(TableView<T> table, Loader<T> loader) {
        this.table = table;
        this.loader = loader;
        sizeBox.getItems().addAll(20, 50, 100);
        sizeBox.setValue(20);
        sizeBox.setOnAction(e -> reload());
        prev.setOnAction(e -> go(page - 1));
        next.setOnAction(e -> go(page + 1));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(8, info, spacer, new Label("Số dòng/trang"), sizeBox, prev, next);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8, 0, 0, 0));
        setCenter(table);
        setBottom(bar);
    }

    public TableView<T> table() {
        return table;
    }

    /** Tải lại từ trang đầu (dùng khi đổi bộ lọc). */
    public void reload() {
        go(0);
    }

    /** Tải lại trang hiện tại (sau khi sửa dữ liệu). */
    public void refresh() {
        go(page);
    }

    private void go(int target) {
        int size = sizeBox.getValue();
        Async.run(this, () -> loader.load(Math.max(0, target), size), p -> {
            page = p.page();
            totalPages = p.totalPages();
            table.getItems().setAll(p.content());
            long from = p.totalElements() == 0 ? 0 : (long) p.page() * p.size() + 1;
            long to = (long) p.page() * p.size() + p.content().size();
            info.setText(p.totalElements() == 0 ? "Không có dữ liệu"
                    : "Hiển thị " + from + "–" + to + " / " + p.totalElements() + " · Trang " + (page + 1) + "/" + Math.max(1, totalPages));
            prev.setDisable(page <= 0);
            next.setDisable(page + 1 >= totalPages);
        });
    }
}
