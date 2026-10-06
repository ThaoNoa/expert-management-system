package com.npcore.ems.desktop.ui.competency;

import com.fasterxml.jackson.core.type.TypeReference;
import com.npcore.ems.desktop.api.Dtos.AssessmentRole;
import com.npcore.ems.desktop.api.Dtos.Code;
import com.npcore.ems.desktop.api.Dtos.CompetencyDefinition;
import com.npcore.ems.desktop.api.Dtos.CompetencyDefinitionRequest;
import com.npcore.ems.desktop.api.Dtos.Scheme;
import com.npcore.ems.desktop.api.Dtos.Standard;
import com.npcore.ems.desktop.api.Page;
import com.npcore.ems.desktop.api.Session;
import com.npcore.ems.desktop.ui.fx.Async;
import com.npcore.ems.desktop.ui.fx.Dialogs;
import com.npcore.ems.desktop.ui.fx.Fmt;
import com.npcore.ems.desktop.ui.fx.Form;
import com.npcore.ems.desktop.ui.fx.Form.Option;
import com.npcore.ems.desktop.ui.fx.PagedTable;
import com.npcore.ems.desktop.ui.fx.Tables;
import com.npcore.ems.desktop.ui.fx.Ui;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Quản lý danh mục định nghĩa năng lực (Competency Definitions catalog). */
public final class CompetencyDefinitionsView extends VBox {

    private final Session session;
    private final PagedTable<CompetencyDefinition> table;
    private List<Scheme> schemes = List.of();
    private List<Standard> standards = List.of();
    private List<AssessmentRole> roles = List.of();
    private List<Code> codes = List.of();

    public CompetencyDefinitionsView(Session session) {
        super(12);
        this.session = session;
        setPadding(new Insets(16));

        Label title = new Label("Danh mục định nghĩa năng lực (Competency Definitions)");
        title.getStyleClass().add("page-title");

        TableView<CompetencyDefinition> tv = Tables.table("Chưa có định nghĩa năng lực");
        tv.getColumns().addAll(List.of(
                Tables.col("Scheme", CompetencyDefinition::schemeCode, 110),
                Tables.col("Tiêu chuẩn", CompetencyDefinition::standardCode, 130),
                Tables.col("Mã Code", (CompetencyDefinition d) -> d.codeValue() == null ? "(Toàn tiêu chuẩn)" : d.codeValue(), 140),
                Tables.col("Vai trò", CompetencyDefinition::roleCode, 100),
                Tables.col("Hiệu lực (tháng)", (CompetencyDefinition d) -> d.defaultValidityMonths() == null ? "—" : d.defaultValidityMonths() + " th", 120),
                Tables.col("Áp dụng từ", (CompetencyDefinition d) -> Fmt.date(d.effectiveFrom()), 110),
                Tables.col("Phiên bản", CompetencyDefinition::version, 90),
                Tables.status("Trạng thái", CompetencyDefinition::status, 120)));
        table = new PagedTable<>(tv, (page, size) -> session.api().competencyDefinitions(null, null, null, page, size));
        VBox.setVgrow(table, Priority.ALWAYS);

        var bar = Ui.toolbar(Ui.button("Tải lại", this::reload), Ui.spacer());
        if (session.has("MASTER_DATA_MANAGE")) {
            Button add = Ui.primary("+ Thêm định nghĩa", () -> openForm(null));
            Button edit = Ui.button("Sửa", () -> openForm(table.table().getSelectionModel().getSelectedItem()));
            edit.disableProperty().bind(table.table().getSelectionModel().selectedItemProperty().isNull());

            Button activate = Ui.button("Kích hoạt", () -> {
                CompetencyDefinition sel = table.table().getSelectionModel().getSelectedItem();
                if (sel != null) {
                    Async.exec(this, () -> session.api().activateCompetencyDefinition(sel.id()), this::reload);
                }
            });
            activate.disableProperty().bind(table.table().getSelectionModel().selectedItemProperty().isNull());

            Button retire = Ui.danger("Ngừng hiệu lực", () -> {
                CompetencyDefinition sel = table.table().getSelectionModel().getSelectedItem();
                if (sel != null && Dialogs.confirm("Ngừng hiệu lực định nghĩa năng lực này?")) {
                    Async.exec(this, () -> session.api().retireCompetencyDefinition(sel.id()), this::reload);
                }
            });
            retire.disableProperty().bind(table.table().getSelectionModel().selectedItemProperty().isNull());

            bar.getChildren().addAll(edit, activate, retire, add);
        }

        getChildren().addAll(title, bar, table);
        loadLookups();
        reload();
    }

    private void reload() {
        table.reload();
    }

    private void loadLookups() {
        Async.run(this, () -> {
            var sc = session.api().list("schemes", new TypeReference<List<Scheme>>() {});
            var st = session.api().list("standards", new TypeReference<List<Standard>>() {});
            var ar = session.api().list("assessment-roles", new TypeReference<List<AssessmentRole>>() {});
            return new Object[] {sc, st, ar};
        }, r -> {
            schemes = (List<Scheme>) r[0];
            standards = (List<Standard>) r[1];
            roles = (List<AssessmentRole>) r[2];
        });
    }

    private void openForm(CompetencyDefinition existing) {
        List<Option<UUID>> schemeOpts = schemes.stream()
                .map(s -> new Option<>(s.id(), s.schemeCode() + " - " + s.schemeName())).toList();
        List<Option<UUID>> stdOpts = standards.stream()
                .map(s -> new Option<>(s.id(), s.standardCode() + " - " + s.standardName())).toList();
        List<Option<UUID>> roleOpts = roles.stream()
                .map(r -> new Option<>(r.id(), r.roleCode() + " - " + r.roleName())).toList();

        Form f = new Form()
                .choice("scheme", "Scheme", schemeOpts, true)
                .choice("standard", "Tiêu chuẩn", stdOpts, true)
                .choice("role", "Vai trò đánh giá", roleOpts, true)
                .text("validity", "Thời hạn mặc định (tháng, vd 36)", false)
                .date("effectiveFrom", "Hiệu lực từ", true)
                .date("effectiveTo", "Hiệu lực đến", false)
                .text("version", "Phiên bản (vd 1.0)", true);

        if (existing != null) {
            f.set("scheme", existing.schemeId())
             .set("standard", existing.standardId())
             .set("role", existing.assessmentRoleId())
             .set("validity", existing.defaultValidityMonths() == null ? "" : existing.defaultValidityMonths().toString())
             .set("effectiveFrom", existing.effectiveFrom())
             .set("effectiveTo", existing.effectiveTo())
             .set("version", existing.version());
        } else {
            f.set("effectiveFrom", LocalDate.now()).set("version", "1");
        }

        f.showDialog(existing == null ? "Thêm định nghĩa năng lực" : "Sửa định nghĩa", "Lưu", () -> {
            Integer val = f.integer("validity");
            CompetencyDefinitionRequest req = new CompetencyDefinitionRequest(
                    f.value("scheme"),
                    f.value("standard"),
                    null, // codeId: toàn tiêu chuẩn nếu null
                    f.value("role"),
                    val == null ? null : val.shortValue(),
                    f.date("effectiveFrom"),
                    f.date("effectiveTo"),
                    f.str("version"),
                    existing == null ? "ACTIVE" : existing.status(),
                    null);

            if (existing == null) {
                session.api().createCompetencyDefinition(req);
            } else {
                session.api().updateCompetencyDefinition(existing.id(), req);
            }
            return Boolean.TRUE;
        }, ok -> reload());
    }
}
