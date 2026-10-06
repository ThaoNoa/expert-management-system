package com.npcore.ems.desktop.ui.fx;

import com.npcore.ems.desktop.api.Dtos.ImportResult;
import com.npcore.ems.desktop.api.Dtos.ImportResult.RowError;
import java.util.List;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Hiển thị kết quả import: tổng số dòng, số dòng đã nhập, bảng lỗi theo số dòng trong file. */
public final class ImportResultView extends VBox {

    private final Label summary = new Label();
    private final TableView<RowError> errors = Tables.table("Không có lỗi");

    public ImportResultView() {
        super(8);
        summary.getStyleClass().add("import-summary");
        errors.getColumns().addAll(List.of(Tables.col("Dòng", RowError::row, 70), Tables.col("Lỗi", RowError::message, 600)));
        VBox.setVgrow(errors, Priority.ALWAYS);
        getChildren().addAll(summary, errors);
        setVisible(false);
    }

    public void show(ImportResult r) {
        setVisible(true);
        summary.setText("Tổng " + r.total() + " dòng · đã nhập " + r.imported() + " · lỗi " + r.errors().size());
        summary.getStyleClass().removeAll("ok", "danger");
        summary.getStyleClass().add(r.errors().isEmpty() ? "ok" : "danger");
        errors.getItems().setAll(r.errors());
    }
}
