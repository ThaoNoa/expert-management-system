package com.npcore.ems.desktop.ui.master;

import com.npcore.ems.desktop.api.Dtos.Code;
import com.npcore.ems.desktop.api.Dtos.CodeSet;
import com.npcore.ems.desktop.api.Dtos.Scheme;
import com.npcore.ems.desktop.api.Session;
import com.npcore.ems.desktop.ui.fx.Async;
import com.npcore.ems.desktop.ui.fx.Dialogs;
import com.npcore.ems.desktop.ui.fx.Fmt;
import com.npcore.ems.desktop.ui.fx.Form;
import com.npcore.ems.desktop.ui.fx.Form.Option;
import com.npcore.ems.desktop.ui.fx.ImportResultView;
import com.npcore.ems.desktop.ui.fx.Lookups;
import com.npcore.ems.desktop.ui.fx.Tables;
import com.npcore.ems.desktop.ui.fx.Ui;
import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/**
 * Bộ mã Code theo scheme (FR-3.1): bộ mã có phiên bản; cây code; thêm code; import Excel/CSV; kích hoạt.
 * Bộ mã chỉ sửa được khi ở trạng thái Nháp – bộ đã kích hoạt được giữ nguyên giá trị pháp lý.
 */
public final class CodeSetsView extends VBox {

    private final Session session;
    private final TableView<CodeSet> sets = Tables.table("Chưa có bộ mã");
    private final TreeView<Code> tree = new TreeView<>();
    private final Label treeTitle = new Label("Chọn một bộ mã để xem cây code");
    private final ImportResultView importResult = new ImportResultView();
    private final boolean write;
    private List<Code> currentCodes = List.of();
    private List<Scheme> schemes = List.of();

    public CodeSetsView(Session session) {
        super(12);
        this.session = session;
        this.write = session.has("MASTER_DATA_MANAGE");
        setPadding(new Insets(16));

        sets.getColumns().addAll(List.of(
                Tables.col("Scheme", CodeSet::schemeCode, 100), Tables.col("Phiên bản", CodeSet::version, 100),
                Tables.col("Hiệu lực từ", c -> Fmt.date(c.effectiveFrom()), 100),
                Tables.col("Hiệu lực đến", c -> Fmt.date(c.effectiveTo()), 100),
                Tables.col("Số code", CodeSet::codeCount, 70), Tables.status("Trạng thái", CodeSet::status, 120),
                Tables.col("Căn cứ", CodeSet::sourceRef, 140)));
        sets.getSelectionModel().selectedItemProperty().addListener((o, a, s) -> loadCodes());
        tree.setShowRoot(false);
        tree.setCellFactory(tv -> new javafx.scene.control.TreeCell<>() {
            @Override
            protected void updateItem(Code c, boolean empty) {
                super.updateItem(c, empty);
                setText(empty || c == null ? null : c.codeValue() + " – " + c.codeName()
                        + (c.riskCategory() == null ? "" : "  [" + c.riskCategory() + "]")
                        + ("INACTIVE".equals(c.status()) ? "  (ngừng dùng)" : ""));
            }
        });

        var setBar = Ui.toolbar(Ui.button("Tải lại", this::reload), Ui.spacer());
        var codeBar = Ui.toolbar(treeTitle, Ui.spacer());
        if (write) {
            Button activate = Ui.button("Kích hoạt", this::activate);
            activate.disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(() -> {
                CodeSet s = sets.getSelectionModel().getSelectedItem();
                return s == null || !"DRAFT".equals(s.status());
            }, sets.getSelectionModel().selectedItemProperty()));
            setBar.getChildren().addAll(activate, Ui.primary("+ Tạo bộ mã", this::createSet));
            Button addCode = Ui.primary("+ Thêm code", this::addCode);
            Button importBtn = Ui.button("Import Excel/CSV…", this::importCodes);
            var notDraft = javafx.beans.binding.Bindings.createBooleanBinding(() -> {
                CodeSet s = sets.getSelectionModel().getSelectedItem();
                return s == null || !"DRAFT".equals(s.status());
            }, sets.getSelectionModel().selectedItemProperty());
            addCode.disableProperty().bind(notDraft);
            importBtn.disableProperty().bind(notDraft);
            codeBar.getChildren().addAll(importBtn, addCode);
        }
        VBox upper = new VBox(8, setBar, sets);
        VBox.setVgrow(sets, Priority.ALWAYS);
        VBox lower = new VBox(8, codeBar, tree, importResult,
                Ui.hint("File import: cột code_value, code_name, parent_code, risk_category. Code cha có thể nằm sau code con. "
                        + "Có lỗi ở bất kỳ dòng nào thì không nhập dòng nào."));
        VBox.setVgrow(tree, Priority.ALWAYS);
        importResult.setManaged(false);
        upper.setPadding(new Insets(6));
        lower.setPadding(new Insets(6));
        SplitPane split = new SplitPane(upper, lower);
        split.setOrientation(javafx.geometry.Orientation.VERTICAL);
        split.setDividerPositions(0.4);
        VBox.setVgrow(split, Priority.ALWAYS);
        getChildren().addAll(Ui.title("Bộ mã & Code"), split);
        reload();
    }

    private void reload() {
        UUID keep = sets.getSelectionModel().getSelectedItem() == null ? null : sets.getSelectionModel().getSelectedItem().id();
        Async.run(this, () -> new Object[] {session.api().schemes(), session.api().codeSets(null)}, r -> {
            @SuppressWarnings("unchecked") List<Scheme> sc = (List<Scheme>) r[0];
            @SuppressWarnings("unchecked") List<CodeSet> cs = (List<CodeSet>) r[1];
            schemes = sc;
            sets.getItems().setAll(cs);
            if (keep != null) cs.stream().filter(c -> c.id().equals(keep)).findFirst().ifPresent(c -> sets.getSelectionModel().select(c));
        });
    }

    private void loadCodes() {
        CodeSet s = sets.getSelectionModel().getSelectedItem();
        importResult.setVisible(false);
        importResult.setManaged(false);
        if (s == null) {
            tree.setRoot(null);
            return;
        }
        treeTitle.setText("Code của " + s.schemeCode() + " " + s.version() + " (" + Fmt.label(s.status()) + ")");
        Async.run(tree, () -> session.api().codes(s.id()), codes -> {
            currentCodes = codes;
            TreeItem<Code> root = new TreeItem<>();
            Map<UUID, TreeItem<Code>> items = new HashMap<>();
            for (Code c : codes) items.put(c.id(), new TreeItem<>(c));
            for (Code c : codes) {
                TreeItem<Code> parent = c.parentId() == null ? root : items.getOrDefault(c.parentId(), root);
                parent.getChildren().add(items.get(c.id()));
                items.get(c.id()).setExpanded(true);
            }
            tree.setRoot(root);
        });
    }

    private void createSet() {
        Form f = new Form()
                .choice("scheme", "Scheme", schemes.stream().map(s -> new Option<>(s.id(), s.schemeCode() + " – " + s.schemeName())).toList(), true)
                .text("version", "Phiên bản bộ mã (VD 2026.01)", true).date("from", "Hiệu lực từ", true)
                .date("to", "Hiệu lực đến", false).text("source", "Căn cứ (SOP / IAF…)", false)
                .note("Bộ mã mới ở trạng thái Nháp; thêm/import code xong thì Kích hoạt. Bộ đang dùng của scheme sẽ chuyển sang Hết hiệu lực.");
        f.showDialog("Tạo bộ mã", "Tạo", () -> {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("schemeId", f.value("scheme"));
            b.put("version", f.str("version"));
            b.put("effectiveFrom", f.date("from"));
            b.put("effectiveTo", f.date("to"));
            b.put("sourceRef", f.str("source"));
            return session.api().createCodeSet(b);
        }, cs -> reload());
    }

    private void activate() {
        CodeSet s = sets.getSelectionModel().getSelectedItem();
        if (s == null || !Dialogs.confirm("Kích hoạt bộ mã " + s.schemeCode() + " " + s.version()
                + "? Bộ mã đang dùng của scheme này sẽ chuyển sang Hết hiệu lực và không sửa được nữa.")) return;
        Async.run(this, () -> session.api().activateCodeSet(s.id()), r -> reload());
    }

    private void addCode() {
        CodeSet s = sets.getSelectionModel().getSelectedItem();
        if (s == null) return;
        Form f = new Form().text("value", "Mã code", true).text("name", "Tên code", true)
                .choice("parent", "Code cha", Lookups.withNone(currentCodes.stream()
                        .map(c -> new Option<>(c.codeValue(), c.codeValue() + " – " + c.codeName())).toList()), false)
                .text("risk", "Nhóm rủi ro", false);
        f.showDialog("Thêm code – " + s.schemeCode() + " " + s.version(), "Lưu", () -> {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("codeValue", f.str("value"));
            b.put("codeName", f.str("name"));
            b.put("parentCode", f.value("parent"));
            b.put("riskCategory", f.str("risk"));
            return session.api().addCode(s.id(), b);
        }, c -> {
            loadCodes();
            reload();
        });
    }

    private void importCodes() {
        CodeSet s = sets.getSelectionModel().getSelectedItem();
        if (s == null) return;
        FileChooser fc = new FileChooser();
        fc.setTitle("Chọn file code");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel / CSV", "*.xlsx", "*.csv"));
        File file = fc.showOpenDialog(getScene().getWindow());
        if (file == null) return;
        Async.run(this, () -> session.api().importCodes(s.id(), file.toPath()), r -> {
            importResult.setManaged(true);
            importResult.show(r);
            loadCodes();
            reload();
        });
    }
}
