package com.npcore.ems.desktop.ui.competency;

import com.npcore.ems.desktop.api.Dtos.AssessmentRole;
import com.npcore.ems.desktop.api.Dtos.MatrixCell;
import com.npcore.ems.desktop.api.Dtos.MatrixColumn;
import com.npcore.ems.desktop.api.Dtos.MatrixResponse;
import com.npcore.ems.desktop.api.Dtos.MatrixRow;
import com.npcore.ems.desktop.api.Dtos.Standard;
import com.npcore.ems.desktop.api.Session;
import com.npcore.ems.desktop.ui.Navigator;
import com.npcore.ems.desktop.ui.expert.ExpertDetailView;
import com.npcore.ems.desktop.ui.fx.Async;
import com.npcore.ems.desktop.ui.fx.Dialogs;
import com.npcore.ems.desktop.ui.fx.Fmt;
import com.npcore.ems.desktop.ui.fx.Form.Option;
import com.npcore.ems.desktop.ui.fx.Tables;
import com.npcore.ems.desktop.ui.fx.Ui;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import javafx.animation.PauseTransition;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.Duration;

/**
 * Ma trận năng lực của một tiêu chuẩn: dòng = chuyên gia, cột = ★ toàn tiêu chuẩn + các code có định nghĩa năng lực.
 * Ô = vai trò được phê duyệt (LA / AU / TE…). Đánh dấu: kế thừa từ code cha (*), sắp hết hạn, đã hết hạn,
 * chuyên gia tạm dừng đánh giá. Nhấp đúp mở hồ sơ; xuất CSV để mở bằng Excel.
 */
public final class CompetencyMatrixView extends VBox {

    private final Session session;
    private final Navigator nav;
    private final TableView<MatrixRow> table = Tables.table("Chưa có chuyên gia nào được phê duyệt năng lực cho tiêu chuẩn này");
    private final ComboBox<Option<UUID>> standard = new ComboBox<>();
    private final ComboBox<Option<UUID>> role = new ComboBox<>();
    private final CheckBox showExpired = new CheckBox("Hiện cả hết hạn");
    private final CheckBox onlyUsed = new CheckBox("Ẩn code trống");
    private final TextField find = new TextField();
    private final Label summary = Ui.hint("");
    private MatrixResponse data;

    public CompetencyMatrixView(Session session, Navigator nav) {
        super(12);
        this.session = session;
        this.nav = nav;
        setPadding(new Insets(16));

        standard.setPromptText("Chọn tiêu chuẩn");
        standard.setPrefWidth(250);
        onlyUsed.setSelected(true);
        onlyUsed.setTooltip(new Tooltip("Ẩn các cột code chưa có chuyên gia nào được phê duyệt"));
        onlyUsed.setOnAction(e -> applyFilter());
        role.setPromptText("Vai trò");
        role.setPrefWidth(170);
        showExpired.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        onlyUsed.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        find.setPromptText("Lọc theo tên / mã chuyên gia…");
        find.setPrefWidth(190);
        standard.setOnAction(e -> load());
        role.setOnAction(e -> load());
        showExpired.setOnAction(e -> load());
        PauseTransition debounce = new PauseTransition(Duration.millis(250));
        debounce.setOnFinished(e -> applyFilter());
        find.textProperty().addListener((o, a, b) -> debounce.playFromStart());
        Tables.onOpen(table, r -> nav.open("expert:" + r.expertId(), r.expertCode() + " · " + r.expertName(),
                () -> new ExpertDetailView(session, nav, r.expertId())));
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

        Label legend = Ui.hint("Ô ghi vai trò được phê duyệt (LA, AU, TE…).  * = có nhờ code cha (code cha bao code con)  ·  "
                + "nền cam = sắp hết hạn (≤ 60 ngày)  ·  nền đỏ = đã hết hạn  ·  dòng vàng = chuyên gia đang tạm dừng đánh giá, "
                + "không được chọn vào đoàn.  Nhấp đúp để mở hồ sơ.");
        legend.setWrapText(true);
        VBox.setVgrow(table, Priority.ALWAYS);
        getChildren().addAll(Ui.title("Ma trận năng lực"),
                Ui.toolbar(standard, role, showExpired, onlyUsed, find, Ui.spacer(),
                        Ui.button("Tải lại", this::load), Ui.button("Xuất Excel (CSV)", this::export)),
                legend, table, summary);
        table.setRowFactory(tv -> new javafx.scene.control.TableRow<>() {
            @Override
            protected void updateItem(MatrixRow r, boolean empty) {
                super.updateItem(r, empty);
                getStyleClass().remove("matrix-suspended");
                if (!empty && r != null && "SUSPENDED".equals(r.expertStatus())) getStyleClass().add("matrix-suspended");
            }
        });

        Async.run(this, () -> new Object[] {session.api().standards(null), session.api().assessmentRoles()}, r -> {
            @SuppressWarnings("unchecked") List<Standard> stds = (List<Standard>) r[0];
            @SuppressWarnings("unchecked") List<AssessmentRole> roles = (List<AssessmentRole>) r[1];
            standard.getItems().setAll(stds.stream()
                    .sorted(Comparator.comparing(s -> naturalKey(s.standardCode())))
                    .map(s -> new Option<>(s.id(), s.standardCode() + " – " + s.standardName())).toList());
            List<Option<UUID>> ro = new ArrayList<>();
            ro.add(new Option<>(null, "Tất cả vai trò"));
            roles.forEach(a -> ro.add(new Option<>(a.id(), a.roleCode() + " – " + a.roleName())));
            role.getItems().setAll(ro);
            if (!standard.getItems().isEmpty()) standard.getSelectionModel().selectFirst();
        });
    }

    private void load() {
        Option<UUID> s = standard.getValue();
        if (s == null) return;
        UUID roleId = role.getValue() == null ? null : role.getValue().value();
        Async.run(this, () -> session.api().competencyMatrix(s.value(), roleId, showExpired.isSelected()), m -> {
            data = m;
            applyFilter();
        });
    }

    private void applyFilter() {
        if (data == null) return;
        String needle = norm(find.getText());
        List<MatrixRow> rows = data.rows().stream()
                .filter(r -> needle.isEmpty() || norm(r.expertName()).contains(needle) || norm(r.expertCode()).contains(needle))
                .toList();
        buildColumns(visibleColumns());
        table.getItems().setAll(rows);
        long suspended = rows.stream().filter(r -> "SUSPENDED".equals(r.expertStatus())).count();
        summary.setText(rows.size() + " chuyên gia" + (suspended > 0 ? " (" + suspended + " đang tạm dừng)" : "")
                + " · " + (data.columns().size() - 1) + " code có định nghĩa năng lực"
                + (onlyUsed.isSelected() ? " (đang hiện " + (visibleColumns().size() - 1) + " code có chuyên gia)" : "")
                + (data.parentCoversChild() ? " · scheme áp dụng quy tắc code cha bao code con" : ""));
    }

    private List<MatrixColumn> visibleColumns() {
        if (!onlyUsed.isSelected()) return data.columns();
        return data.columns().stream().filter(c -> "*".equals(c.codeValue()) || data.rows().stream()
                .anyMatch(r -> r.cells() != null && r.cells().stream().anyMatch(x -> c.codeValue().equals(x.codeValue())))).toList();
    }

    private void buildColumns(List<MatrixColumn> columns) {
        table.getColumns().clear();
        TableColumn<MatrixRow, String> code = Tables.col("Mã CG", MatrixRow::expertCode, 80);
        TableColumn<MatrixRow, String> name = Tables.col("Họ và tên", MatrixRow::expertName, 170);
        TableColumn<MatrixRow, String> status = Tables.col("Trạng thái", r -> "SUSPENDED".equals(r.expertStatus())
                ? "Tạm dừng" + (r.suspendedUntil() == null ? "" : " đến " + Fmt.date(r.suspendedUntil()))
                : Fmt.label(r.expertStatus()), 130);
        table.getColumns().addAll(List.of(code, name, status));
        for (MatrixColumn col : columns) {
            TableColumn<MatrixRow, List<MatrixCell>> c = new TableColumn<>();
            Label header = new Label("*".equals(col.codeValue()) ? "★ Toàn TC" : col.codeValue());
            header.setTooltip(new Tooltip("*".equals(col.codeValue()) ? "Năng lực áp dụng cho toàn tiêu chuẩn (VD Lead Auditor)"
                    : col.codeValue() + " – " + col.codeName() + (col.parentCode() == null ? "" : "\nCode cha: " + col.parentCode())));
            c.setGraphic(header);
            c.setPrefWidth("*".equals(col.codeValue()) ? 90 : 70);
            c.setSortable(false);
            c.setCellValueFactory(v -> new SimpleObjectProperty<>(cellsOf(v.getValue(), col.codeValue())));
            c.setCellFactory(tc -> new MatrixTableCell(col));
            table.getColumns().add(c);
        }
    }

    private static List<MatrixCell> cellsOf(MatrixRow r, String codeValue) {
        return r.cells() == null ? List.of() : r.cells().stream().filter(x -> codeValue.equals(x.codeValue()))
                .sorted(Comparator.comparing(MatrixCell::roleCode)).toList();
    }

    private static String text(List<MatrixCell> cells) {
        return cells.stream().map(x -> x.roleCode() + (x.inherited() ? "*" : "")).distinct().collect(Collectors.joining(" "));
    }

    /** Ô ma trận: vai trò + màu theo hạn; tooltip chi tiết. */
    private static final class MatrixTableCell extends TableCell<MatrixRow, List<MatrixCell>> {
        private final MatrixColumn col;

        MatrixTableCell(MatrixColumn col) {
            this.col = col;
            setAlignment(Pos.CENTER);
        }

        @Override
        protected void updateItem(List<MatrixCell> cells, boolean empty) {
            super.updateItem(cells, empty);
            getStyleClass().removeAll("matrix-ok", "matrix-soon", "matrix-expired", "matrix-inherited");
            setTooltip(null);
            if (empty || cells == null || cells.isEmpty()) {
                setText(null);
                return;
            }
            setText(text(cells));
            boolean allExpired = cells.stream().allMatch(MatrixCell::expired);
            boolean soon = cells.stream().anyMatch(MatrixCell::expiringSoon);
            boolean inherited = cells.stream().allMatch(MatrixCell::inherited);
            getStyleClass().add(allExpired ? "matrix-expired" : soon ? "matrix-soon" : inherited ? "matrix-inherited" : "matrix-ok");
            StringBuilder tip = new StringBuilder("*".equals(col.codeValue()) ? "Toàn tiêu chuẩn" : "Code " + col.codeValue() + " – " + col.codeName());
            for (MatrixCell x : cells) {
                tip.append("\n• ").append(x.roleCode()).append(" – ").append(Fmt.label(x.level()))
                        .append(", hiệu lực ").append(Fmt.date(x.effectiveFrom())).append(" → ")
                        .append(x.effectiveTo() == null ? "không thời hạn" : Fmt.date(x.effectiveTo()));
                if (x.expired()) tip.append(" (ĐÃ HẾT HẠN)");
                else if (x.expiringSoon()) tip.append(" (sắp hết hạn)");
                if (x.inherited()) tip.append(" – có nhờ code cha ").append(col.parentCode());
            }
            setTooltip(new Tooltip(tip.toString()));
        }
    }

    // ------------------------------------------------------------------ xuất CSV (mở bằng Excel)

    private void export() {
        if (data == null || table.getItems().isEmpty()) {
            Dialogs.info("Chưa có dữ liệu để xuất.");
            return;
        }
        FileChooser fc = new FileChooser();
        fc.setTitle("Xuất ma trận năng lực");
        fc.setInitialFileName("ma-tran-nang-luc-" + data.standardCode().replaceAll("[^A-Za-z0-9]+", "-") + ".csv");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV (Excel)", "*.csv"));
        File file = fc.showSaveDialog(getScene().getWindow());
        if (file == null) return;
        StringBuilder sb = new StringBuilder("﻿");                      // BOM để Excel đọc đúng tiếng Việt
        List<String> head = new ArrayList<>(List.of("Mã CG", "Họ và tên", "Trạng thái"));
        visibleColumns().forEach(c -> head.add("*".equals(c.codeValue()) ? "Toàn tiêu chuẩn" : "Code " + c.codeValue()));
        sb.append(csv(head)).append("\r\n");
        for (MatrixRow r : table.getItems()) {
            List<String> line = new ArrayList<>(List.of(r.expertCode(), r.expertName(),
                    "SUSPENDED".equals(r.expertStatus()) ? "Tạm dừng" : Fmt.label(r.expertStatus())));
            for (MatrixColumn c : visibleColumns()) {
                List<MatrixCell> cells = cellsOf(r, c.codeValue());
                line.add(text(cells) + (cells.stream().allMatch(MatrixCell::expired) && !cells.isEmpty() ? " (hết hạn)" : ""));
            }
            sb.append(csv(line)).append("\r\n");
        }
        try {
            Files.writeString(file.toPath(), sb.toString(), StandardCharsets.UTF_8);
            Dialogs.info("Đã xuất " + table.getItems().size() + " chuyên gia ra\n" + file.getAbsolutePath());
        } catch (IOException ex) {
            Dialogs.info("Không ghi được file: " + ex.getMessage());
        }
    }

    private static String csv(List<String> cells) {
        return cells.stream().map(v -> v == null ? "" : v)
                .map(v -> v.contains(",") || v.contains("\"") ? "\"" + v.replace("\"", "\"\"") + "\"" : v)
                .collect(Collectors.joining(","));
    }

    /** "ISO 9001" trước "ISO 14001": đệm số để so sánh tự nhiên. */
    private static String naturalKey(String s) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+").matcher(s == null ? "" : s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) m.appendReplacement(sb, String.format("%08d", Long.parseLong(m.group())));
        m.appendTail(sb);
        return sb.toString();
    }

    private static String norm(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replace('đ', 'd').trim();
    }
}
