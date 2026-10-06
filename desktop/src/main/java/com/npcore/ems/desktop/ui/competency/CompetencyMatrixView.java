package com.npcore.ems.desktop.ui.competency;

import com.fasterxml.jackson.core.type.TypeReference;
import com.npcore.ems.desktop.api.Dtos.Code;
import com.npcore.ems.desktop.api.Dtos.MatrixCell;
import com.npcore.ems.desktop.api.Dtos.MatrixRow;
import com.npcore.ems.desktop.api.Dtos.Standard;
import com.npcore.ems.desktop.api.Session;
import com.npcore.ems.desktop.ui.Navigator;
import com.npcore.ems.desktop.ui.expert.ExpertDetailView;
import com.npcore.ems.desktop.ui.fx.Async;
import com.npcore.ems.desktop.ui.fx.Fmt;
import com.npcore.ems.desktop.ui.fx.Tables;
import com.npcore.ems.desktop.ui.fx.Ui;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/**
 * Màn hình tra cứu Ma trận năng lực chuyên gia dạng bảng 2 chiều (2D Competency Matrix):
 * - Trục dọc (Dòng): Danh sách Chuyên gia
 * - Trục ngang (Cột): Các Mã Code của tiêu chuẩn & Năng lực chung toàn tiêu chuẩn
 * - Ô giao nhau: Vai trò & Cấp độ được phê duyệt (LA, AU, TE)
 */
public final class CompetencyMatrixView extends VBox {

    private final Session session;
    private final Navigator nav;
    private final TableView<MatrixRow> table = Tables.table("Chọn một tiêu chuẩn để xem ma trận năng lực");
    private final ComboBox<Standard> standardCombo = new ComboBox<>();

    public CompetencyMatrixView(Session session, Navigator nav) {
        super(12);
        this.session = session;
        this.nav = nav;
        setPadding(new Insets(16));

        Label title = new Label("Ma trận năng lực chuyên gia (Competency Matrix)");
        title.getStyleClass().add("page-title");

        standardCombo.setPromptText("— Chọn tiêu chuẩn —");
        standardCombo.setPrefWidth(260);
        standardCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Standard s) {
                return s == null ? "" : s.standardCode() + " - " + s.standardName();
            }
            @Override
            public Standard fromString(String string) {
                return null;
            }
        });
        standardCombo.getSelectionModel().selectedItemProperty().addListener((o, a, s) -> loadMatrix());

        VBox.setVgrow(table, Priority.ALWAYS);
        Tables.onOpen(table, row -> nav.open("expert-" + row.expertId(), row.expertName(),
                () -> new ExpertDetailView(session, nav, row.expertId())));

        var bar = Ui.toolbar(
                new Label("Tiêu chuẩn:"), standardCombo,
                Ui.button("Tải lại", this::loadMatrix),
                Ui.spacer(),
                Ui.hint("Bảng 2 chiều: Dòng = Chuyên gia | Cột = Mã ngành (Code) | Nhấp đúp để mở hồ sơ"));

        getChildren().addAll(title, bar, table);
        loadStandards();
    }

    private void loadStandards() {
        Async.run(this, () -> session.api().list("standards", new TypeReference<List<Standard>>() {}), list -> {
            standardCombo.getItems().setAll(list);
            if (!list.isEmpty()) {
                standardCombo.getSelectionModel().selectFirst();
            }
        });
    }

    private void loadMatrix() {
        Standard sel = standardCombo.getSelectionModel().getSelectedItem();
        if (sel == null) return;

        Async.run(this, () -> {
            var rows = session.api().competencyMatrix(sel.id());
            List<Code> allCodes;
            try {
                allCodes = session.api().list("codes", new TypeReference<List<Code>>() {});
            } catch (Exception ex) {
                allCodes = List.of();
            }
            return new Object[] {rows, allCodes};
        }, result -> {
            List<MatrixRow> rows = (List<MatrixRow>) result[0];
            List<Code> allCodes = (List<Code>) result[1];
            rebuildTableColumns(rows, allCodes);
            table.getItems().setAll(rows);
        });
    }

    private void rebuildTableColumns(List<MatrixRow> rows, List<Code> allCodes) {
        table.getColumns().clear();

        // 1. Các cột thông tin chuyên gia cố định
        table.getColumns().add(Tables.col("Mã CG", MatrixRow::expertCode, 100));
        table.getColumns().add(Tables.col("Họ và tên", MatrixRow::expertName, 180));
        table.getColumns().add(Tables.col("Loại", r -> Fmt.label(r.expertType()), 110));

        // 2. Cột Năng lực chung toàn tiêu chuẩn (LA/AU không theo code)
        TableColumn<MatrixRow, String> generalCol = new TableColumn<>("Toàn tiêu chuẩn");
        generalCol.setPrefWidth(130);
        generalCol.setCellValueFactory(c -> {
            MatrixRow r = c.getValue();
            String val = getRoleForCode(r, "*");
            return new SimpleStringProperty(val.isEmpty() ? "—" : val);
        });
        table.getColumns().add(generalCol);

        // 3. Tập hợp các Mã Code có trong dữ liệu hoặc trong danh mục
        TreeSet<String> distinctCodes = new TreeSet<>();
        for (MatrixRow r : rows) {
            if (r.cells() != null) {
                for (MatrixCell cell : r.cells()) {
                    if (cell.codeValue() != null && !"*".equals(cell.codeValue())) {
                        distinctCodes.add(cell.codeValue());
                    }
                }
            }
        }
        for (Code c : allCodes) {
            distinctCodes.add(c.codeValue());
        }

        // 4. Sinh động các cột Code 2D
        for (String codeVal : distinctCodes) {
            String colTitle = "Code " + codeVal;
            // Tìm tên code nếu có
            allCodes.stream().filter(c -> Objects.equals(c.codeValue(), codeVal)).findFirst()
                    .ifPresent(c -> {
                        // Rút gọn tên nếu dài
                        String shortName = c.codeName().length() > 20 ? c.codeName().substring(0, 18) + "…" : c.codeName();
                    });

            TableColumn<MatrixRow, String> codeCol = new TableColumn<>(colTitle);
            codeCol.setPrefWidth(110);
            codeCol.setCellValueFactory(c -> {
                MatrixRow r = c.getValue();
                String val = getRoleForCode(r, codeVal);
                return new SimpleStringProperty(val.isEmpty() ? "—" : val);
            });
            table.getColumns().add(codeCol);
        }
    }

    private String getRoleForCode(MatrixRow row, String targetCode) {
        if (row.cells() == null) return "";
        return row.cells().stream()
                .filter(cell -> Objects.equals(cell.codeValue(), targetCode))
                .map(cell -> cell.roleCode() + " (" + cell.level() + ")")
                .collect(Collectors.joining(", "));
    }
}
