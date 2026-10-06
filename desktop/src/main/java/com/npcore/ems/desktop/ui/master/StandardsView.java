package com.npcore.ems.desktop.ui.master;

import com.npcore.ems.desktop.api.Dtos.Scheme;
import com.npcore.ems.desktop.api.Dtos.Standard;
import com.npcore.ems.desktop.api.Dtos.StandardVersion;
import com.npcore.ems.desktop.api.Session;
import com.npcore.ems.desktop.ui.fx.Async;
import com.npcore.ems.desktop.ui.fx.Fmt;
import com.npcore.ems.desktop.ui.fx.Form;
import com.npcore.ems.desktop.ui.fx.Form.Option;
import com.npcore.ems.desktop.ui.fx.Tables;
import com.npcore.ems.desktop.ui.fx.Ui;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Tiêu chuẩn và phiên bản tiêu chuẩn (FR-3.2, BR-VER-001). Chọn tiêu chuẩn ở trên → phiên bản ở dưới. */
public final class StandardsView extends VBox {

    private final Session session;
    private final TableView<Standard> standards = Tables.table("Chưa có tiêu chuẩn");
    private final TableView<StandardVersion> versions = Tables.table("Chọn một tiêu chuẩn để xem phiên bản");
    private List<Scheme> schemes = List.of();

    public StandardsView(Session session) {
        super(12);
        this.session = session;
        setPadding(new Insets(16));
        boolean write = session.has("MASTER_DATA_MANAGE");

        standards.getColumns().addAll(List.of(
                Tables.col("Mã", Standard::standardCode, 140), Tables.col("Tên tiêu chuẩn", Standard::standardName, 320),
                Tables.col("Scheme", Standard::schemeCode, 120), Tables.status("Trạng thái", Standard::status, 140)));
        versions.getColumns().addAll(List.of(
                Tables.col("Phiên bản", StandardVersion::version, 120),
                Tables.col("Hiệu lực từ", v -> Fmt.date(v.effectiveFrom()), 110),
                Tables.col("Hiệu lực đến", v -> Fmt.date(v.effectiveTo()), 110),
                Tables.col("Hết chuyển đổi", v -> Fmt.date(v.transitionEnd()), 120),
                Tables.status("Trạng thái", StandardVersion::status, 140)));
        standards.getSelectionModel().selectedItemProperty().addListener((o, a, s) -> loadVersions());

        var top = Ui.toolbar(Ui.button("Tải lại", this::reload), Ui.spacer());
        var bottom = Ui.toolbar(new Label("Phiên bản của tiêu chuẩn đang chọn"), Ui.spacer());
        if (write) {
            Button edit = Ui.button("Sửa", () -> standardForm(standards.getSelectionModel().getSelectedItem()));
            edit.disableProperty().bind(standards.getSelectionModel().selectedItemProperty().isNull());
            top.getChildren().addAll(edit, Ui.primary("+ Thêm tiêu chuẩn", () -> standardForm(null)));
            Button addV = Ui.primary("+ Thêm phiên bản", () -> versionForm(null));
            addV.disableProperty().bind(standards.getSelectionModel().selectedItemProperty().isNull());
            Button editV = Ui.button("Sửa phiên bản", () -> versionForm(versions.getSelectionModel().getSelectedItem()));
            editV.disableProperty().bind(versions.getSelectionModel().selectedItemProperty().isNull());
            bottom.getChildren().addAll(editV, addV);
            Tables.onOpen(standards, this::standardForm);
            Tables.onOpen(versions, this::versionForm);
        }
        VBox upper = new VBox(8, top, standards);
        VBox lower = new VBox(8, bottom, versions);
        VBox.setVgrow(standards, Priority.ALWAYS);
        VBox.setVgrow(versions, Priority.ALWAYS);
        upper.setPadding(new Insets(6));
        lower.setPadding(new Insets(6));
        SplitPane split = new SplitPane(upper, lower);
        split.setOrientation(javafx.geometry.Orientation.VERTICAL);
        split.setDividerPositions(0.55);
        VBox.setVgrow(split, Priority.ALWAYS);
        getChildren().addAll(Ui.title("Tiêu chuẩn"), split);
        reload();
    }

    private void reload() {
        Async.run(this, () -> new Object[] {session.api().schemes(), session.api().standards(null)}, r -> {
            @SuppressWarnings("unchecked") List<Scheme> sc = (List<Scheme>) r[0];
            @SuppressWarnings("unchecked") List<Standard> st = (List<Standard>) r[1];
            schemes = sc;
            standards.getItems().setAll(st);
        });
    }

    private void loadVersions() {
        Standard s = standards.getSelectionModel().getSelectedItem();
        if (s == null) {
            versions.getItems().clear();
            return;
        }
        Async.run(versions, () -> session.api().standardVersions(s.id()), rows -> versions.getItems().setAll(rows));
    }

    private void standardForm(Standard existing) {
        Form f = new Form().text("code", "Mã (VD ISO9001)", true).text("name", "Tên tiêu chuẩn", true)
                .choice("scheme", "Scheme", schemes.stream().map(s -> new Option<>(s.id(), s.schemeCode() + " – " + s.schemeName())).toList(), true)
                .codes("status", "Trạng thái", List.of("ACTIVE", "INACTIVE"), true).set("status", "ACTIVE");
        if (existing != null) {
            f.set("code", existing.standardCode()).set("name", existing.standardName()).set("scheme", existing.schemeId())
                    .set("status", existing.status());
        }
        f.showDialog(existing == null ? "Thêm tiêu chuẩn" : "Sửa tiêu chuẩn", "Lưu", () -> {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("standardCode", f.str("code"));
            b.put("standardName", f.str("name"));
            b.put("schemeId", f.value("scheme"));
            b.put("status", f.value("status"));
            return existing == null ? session.api().create("standards", b) : session.api().update("standards", existing.id(), b);
        }, r -> reload());
    }

    private void versionForm(StandardVersion existing) {
        Standard std = standards.getSelectionModel().getSelectedItem();
        if (std == null) return;
        Form f = new Form().text("version", "Phiên bản (VD 2015)", true).date("from", "Hiệu lực từ", true)
                .date("to", "Hiệu lực đến", false).date("transition", "Hết giai đoạn chuyển đổi", false)
                .codes("status", "Trạng thái", List.of("DRAFT", "ACTIVE", "TRANSITION", "WITHDRAWN"), true).set("status", "ACTIVE");
        if (existing != null) {
            f.set("version", existing.version()).set("from", existing.effectiveFrom()).set("to", existing.effectiveTo())
                    .set("transition", existing.transitionEnd()).set("status", existing.status());
        }
        f.showDialog((existing == null ? "Thêm phiên bản – " : "Sửa phiên bản – ") + std.standardCode(), "Lưu", () -> {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("version", f.str("version"));
            b.put("effectiveFrom", f.date("from"));
            b.put("effectiveTo", f.date("to"));
            b.put("transitionEnd", f.date("transition"));
            b.put("status", f.value("status"));
            return existing == null ? session.api().addStandardVersion(std.id(), b) : session.api().updateStandardVersion(existing.id(), b);
        }, r -> loadVersions());
    }
}
