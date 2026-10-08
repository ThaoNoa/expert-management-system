package com.npcore.ems.desktop.ui.competency;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.npcore.ems.desktop.api.Dtos.AssessmentRole;
import com.npcore.ems.desktop.api.Dtos.BulkDefinitionRequest;
import com.npcore.ems.desktop.api.Dtos.BulkResult;
import com.npcore.ems.desktop.api.Dtos.Code;
import com.npcore.ems.desktop.api.Dtos.CompetencyDefinition;
import com.npcore.ems.desktop.api.Dtos.CompetencyDefinitionRequest;
import com.npcore.ems.desktop.api.Dtos.Standard;
import com.npcore.ems.desktop.api.Json;
import com.npcore.ems.desktop.api.Session;
import com.npcore.ems.desktop.ui.fx.Async;
import com.npcore.ems.desktop.ui.fx.Dialogs;
import com.npcore.ems.desktop.ui.fx.Fmt;
import com.npcore.ems.desktop.ui.fx.Form;
import com.npcore.ems.desktop.ui.fx.Form.Option;
import com.npcore.ems.desktop.ui.fx.Lookups;
import com.npcore.ems.desktop.ui.fx.PagedTable;
import com.npcore.ems.desktop.ui.fx.Tables;
import com.npcore.ems.desktop.ui.fx.Ui;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javafx.animation.PauseTransition;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Danh mục định nghĩa năng lực: tổ hợp Tiêu chuẩn + Code (hoặc toàn tiêu chuẩn) + Vai trò đánh giá, kèm tiêu chí đầu vào
 * và thời hạn hiệu lực. Là "khung" để đăng ký năng lực cho chuyên gia và để dựng ma trận năng lực.
 */
public final class CompetencyDefinitionsView extends VBox {

    private final Session session;
    private final boolean write;
    private Lookups lookups;
    private List<AssessmentRole> roles = List.of();
    private PagedTable<CompetencyDefinition> table;
    private final ComboBox<Option<UUID>> stdFilter = new ComboBox<>();
    private final ComboBox<Option<UUID>> roleFilter = new ComboBox<>();
    private final ComboBox<Option<String>> statusFilter = new ComboBox<>();
    private final TextField q = new TextField();

    public CompetencyDefinitionsView(Session session) {
        super(12);
        this.session = session;
        this.write = session.has("MASTER_DATA_MANAGE");
        setPadding(new Insets(16));
        getChildren().addAll(Ui.title("Định nghĩa năng lực"), new Label("Đang tải…"));
        Async.run(this, () -> new Object[] {Lookups.load(session), session.api().assessmentRoles()}, r -> {
            lookups = (Lookups) r[0];
            @SuppressWarnings("unchecked") List<AssessmentRole> ar = (List<AssessmentRole>) r[1];
            roles = ar;
            build();
        });
    }

    private void build() {
        TableView<CompetencyDefinition> tv = Tables.table("Chưa có định nghĩa năng lực phù hợp");
        tv.getColumns().addAll(List.of(
                Tables.col("Tiêu chuẩn", CompetencyDefinition::standardCode, 110),
                Tables.col("Code", d -> d.codeValue() == null ? "★ Toàn tiêu chuẩn" : d.codeValue() + " – " + d.codeName(), 260),
                Tables.col("Vai trò", d -> d.roleCode() + " – " + d.roleName(), 190),
                Tables.col("Tiêu chí đầu vào", d -> criteriaSummary(d.criteria()), 260),
                Tables.col("Hiệu lực NL", d -> d.defaultValidityMonths() == null ? "Không thời hạn" : d.defaultValidityMonths() + " tháng", 110),
                Tables.col("Áp dụng từ", d -> Fmt.date(d.effectiveFrom()), 95),
                Tables.col("Phiên bản", CompetencyDefinition::version, 75),
                Tables.status("Trạng thái", d -> "RETIRED".equals(d.status()) ? "RETIRED" : d.status(), 110)));
        table = new PagedTable<>(tv, (page, size) -> session.api().competencyDefinitions(value(stdFilter), value(roleFilter),
                value(statusFilter), q.getText() == null || q.getText().isBlank() ? null : q.getText().trim(), page, size));

        List<Option<UUID>> stds = new ArrayList<>();
        stds.add(new Option<>(null, "Tất cả tiêu chuẩn"));
        lookups.standards().forEach(s -> stds.add(new Option<>(s.id(), s.standardCode() + " – " + s.standardName())));
        stdFilter.getItems().setAll(stds);
        stdFilter.setPromptText("Tiêu chuẩn");
        List<Option<UUID>> rs = new ArrayList<>();
        rs.add(new Option<>(null, "Tất cả vai trò"));
        roles.forEach(r -> rs.add(new Option<>(r.id(), r.roleCode() + " – " + r.roleName())));
        roleFilter.getItems().setAll(rs);
        roleFilter.setPromptText("Vai trò");
        statusFilter.getItems().setAll(List.of(new Option<>(null, "Tất cả trạng thái"), new Option<>("ACTIVE", "Đang hoạt động"),
                new Option<>("DRAFT", "Nháp"), new Option<>("RETIRED", "Ngừng hiệu lực")));
        statusFilter.setPromptText("Trạng thái");
        q.setPromptText("Tìm code (số hoặc tên)…");
        q.setPrefWidth(200);
        PauseTransition debounce = new PauseTransition(Duration.millis(350));
        debounce.setOnFinished(e -> table.reload());
        q.textProperty().addListener((o, a, b) -> debounce.playFromStart());
        for (ComboBox<?> c : List.of(stdFilter, roleFilter, statusFilter)) c.setOnAction(e -> table.reload());

        HBox bar = Ui.toolbar(stdFilter, roleFilter, statusFilter, q, Ui.spacer());
        HBox actions = Ui.toolbar(Ui.spacer());
        if (write) {
            var sel = tv.getSelectionModel().selectedItemProperty();
            Button edit = Ui.button("Sửa", () -> openForm(sel.get()));
            edit.disableProperty().bind(sel.isNull());
            Button activate = Ui.button("Kích hoạt", () -> Async.exec(this,
                    () -> session.api().activateCompetencyDefinition(sel.get().id()), table::refresh));
            activate.disableProperty().bind(sel.isNull());
            Button retire = Ui.danger("Ngừng hiệu lực", () -> {
                if (Dialogs.confirm("Ngừng hiệu lực định nghĩa năng lực này? Chuyên gia sẽ không đăng ký mới được theo định nghĩa này.")) {
                    Async.exec(this, () -> session.api().retireCompetencyDefinition(sel.get().id()), table::refresh);
                }
            });
            retire.disableProperty().bind(sel.isNull());
            actions.getChildren().addAll(edit, activate, retire, Ui.button("+ Tạo hàng loạt theo code", this::openBulk),
                    Ui.primary("+ Thêm định nghĩa", () -> openForm(null)));
            Tables.onOpen(tv, this::openForm);
        }
        VBox.setVgrow(table, Priority.ALWAYS);
        getChildren().setAll(Ui.title("Định nghĩa năng lực"),
                Ui.hint("Mỗi định nghĩa = Tiêu chuẩn + Code (hoặc ★ toàn tiêu chuẩn, VD Lead Auditor) + Vai trò. "
                        + "Chuyên gia chỉ đăng ký được năng lực có trong danh mục này; ma trận năng lực lấy cột theo các code ở đây."),
                bar, actions, table);
        table.reload();
    }

    // ------------------------------------------------------------------ form 1 định nghĩa

    private void openForm(CompetencyDefinition existing) {
        if (existing != null && "RETIRED".equals(existing.status())) {
            Dialogs.info("Định nghĩa đã ngừng hiệu lực – không sửa được. Hãy tạo định nghĩa mới với phiên bản mới.");
            return;
        }
        Form f = new Form()
                .choice("standard", "Tiêu chuẩn", standardOptions(), true)
                .choice("code", "Code", List.of(), false)
                .choice("role", "Vai trò đánh giá", roleOptions(), true)
                .text("validity", "Thời hạn năng lực (tháng)", false)
                .date("from", "Áp dụng từ", true)
                .date("to", "Áp dụng đến", false)
                .text("version", "Phiên bản", true);
        criteriaFields(f);
        f.note("Để trống Code = năng lực áp dụng cho toàn tiêu chuẩn (VD Lead Auditor). "
                + "Thời hạn năng lực: sau khi GĐCN phê duyệt, năng lực của chuyên gia có hiệu lực trong số tháng này (VD 36).");
        @SuppressWarnings("unchecked") ComboBox<Option<UUID>> std = (ComboBox<Option<UUID>>) f.control("standard");
        std.setOnAction(e -> fillCodes(f, value(std), null));
        if (existing != null) {
            f.set("standard", existing.standardId()).set("role", existing.assessmentRoleId())
                    .set("validity", existing.defaultValidityMonths()).set("from", existing.effectiveFrom())
                    .set("to", existing.effectiveTo()).set("version", existing.version());
            fillCodes(f, existing.standardId(), existing.codeId());
            setCriteria(f, existing.criteria());
        } else {
            f.set("from", LocalDate.now()).set("version", "1").set("validity", 36);
            if (value(stdFilter) != null) {
                f.set("standard", value(stdFilter));
                fillCodes(f, value(stdFilter), null);
            }
        }
        f.showDialog(existing == null ? "Thêm định nghĩa năng lực" : "Sửa định nghĩa năng lực", "Lưu", () -> {
            Integer months = f.integer("validity");
            UUID standardId = f.value("standard");
            CompetencyDefinitionRequest req = new CompetencyDefinitionRequest(schemeOf(standardId), standardId, f.value("code"),
                    f.value("role"), months == null ? null : months.shortValue(), f.date("from"), f.date("to"), f.str("version"),
                    existing == null ? "ACTIVE" : existing.status(), criteria(f));
            return existing == null ? session.api().createCompetencyDefinition(req)
                    : session.api().updateCompetencyDefinition(existing.id(), req);
        }, d -> table.reload());
    }

    private void fillCodes(Form f, UUID standardId, UUID select) {
        @SuppressWarnings("unchecked") ComboBox<Option<UUID>> box = (ComboBox<Option<UUID>>) f.control("code");
        List<Option<UUID>> opts = new ArrayList<>();
        opts.add(new Option<>(null, "★ Toàn tiêu chuẩn (không theo code)"));
        codesOf(standardId).forEach(c -> opts.add(new Option<>(c.id(), indent(c) + c.codeValue() + " – " + c.codeName())));
        box.getItems().setAll(opts);
        box.getItems().stream().filter(o -> Objects.equals(o.value(), select)).findFirst()
                .ifPresent(o -> box.getSelectionModel().select(o));
    }

    // ------------------------------------------------------------------ tạo hàng loạt

    private void openBulk() {
        Form f = new Form()
                .choice("standard", "Tiêu chuẩn", standardOptions(), true)
                .choice("role", "Vai trò đánh giá", roleOptions(), true)
                .check("general", "Kèm năng lực ★ toàn tiêu chuẩn")
                .text("validity", "Thời hạn năng lực (tháng)", false)
                .date("from", "Áp dụng từ", true);
        criteriaFields(f);
        ListView<CodeItem> list = new ListView<>();
        list.setCellFactory(CheckBoxListCell.forListView(CodeItem::selected));
        list.setPrefHeight(260);
        Label count = Ui.hint("");
        Runnable updateCount = () -> count.setText("Đã chọn " + list.getItems().stream().filter(i -> i.selected().get()).count()
                + " / " + list.getItems().size() + " code");
        @SuppressWarnings("unchecked") ComboBox<Option<UUID>> std = (ComboBox<Option<UUID>>) f.control("standard");
        Runnable fill = () -> {
            list.getItems().setAll(codesOf(value(std)).stream().map(c -> {
                CodeItem it = new CodeItem(c, new SimpleBooleanProperty(false));
                it.selected().addListener((o, a, b) -> updateCount.run());
                return it;
            }).toList());
            updateCount.run();
        };
        std.setOnAction(e -> fill.run());
        Button all = Ui.button("Chọn tất cả", () -> list.getItems().forEach(i -> i.selected().set(true)));
        Button none = Ui.button("Bỏ chọn", () -> list.getItems().forEach(i -> i.selected().set(false)));
        VBox codeBox = new VBox(6, new Label("Các code áp dụng:"), Ui.toolbar(all, none, Ui.spacer(), count), list);
        f.node().getChildren().add(codeBox);
        f.set("from", LocalDate.now()).set("validity", 36);
        if (value(stdFilter) != null) {
            f.set("standard", value(stdFilter));
        }
        fill.run();
        f.size(640, 600).showDialog("Tạo hàng loạt định nghĩa năng lực", "Tạo", () -> {
            Set<UUID> ids = new HashSet<>();
            list.getItems().stream().filter(i -> i.selected().get()).forEach(i -> ids.add(i.code().id()));
            Integer months = f.integer("validity");
            return session.api().createCompetencyDefinitionsBulk(new BulkDefinitionRequest(f.value("standard"), f.value("role"),
                    new ArrayList<>(ids), f.bool("general"), months == null ? null : months.shortValue(), f.date("from"), "1",
                    criteria(f)));
        }, (BulkResult r) -> {
            table.reload();
            Dialogs.info("Đã tạo " + r.created() + " định nghĩa năng lực"
                    + (r.skipped() > 0 ? ", bỏ qua " + r.skipped() + " định nghĩa đã có." : "."));
        });
    }

    private record CodeItem(Code code, SimpleBooleanProperty selected) {
        @Override
        public String toString() {
            return indent(code) + code.codeValue() + " – " + code.codeName();
        }
    }

    // ------------------------------------------------------------------ tiêu chí đầu vào (criteria JSON)

    private void criteriaFields(Form f) {
        f.choice("minDegree", "Tiêu chí: trình độ tối thiểu", Lookups.withNone(lookups.degreeOptions()), false)
                .text("minYears", "Tiêu chí: số năm kinh nghiệm tối thiểu", false)
                .text("minAudits", "Tiêu chí: số cuộc đánh giá tối thiểu", false)
                .text("training", "Tiêu chí: đào tạo bắt buộc", false)
                .area("criteriaNote", "Tiêu chí khác", false);
    }

    private void setCriteria(Form f, JsonNode c) {
        if (c == null) return;
        f.set("minDegree", c.hasNonNull("minDegreeLevel") ? c.get("minDegreeLevel").asText() : null)
                .set("minYears", c.hasNonNull("minYearsExperience") ? c.get("minYearsExperience").asText() : null)
                .set("minAudits", c.hasNonNull("minAudits") ? c.get("minAudits").asText() : null)
                .set("training", c.hasNonNull("requiredTraining") ? c.get("requiredTraining").asText() : null)
                .set("criteriaNote", c.hasNonNull("notes") ? c.get("notes").asText() : null);
    }

    private static ObjectNode criteria(Form f) {
        ObjectNode n = Json.MAPPER.createObjectNode();
        if (f.value("minDegree") != null) n.put("minDegreeLevel", (String) f.value("minDegree"));
        Integer years = f.integer("minYears");
        if (years != null) n.put("minYearsExperience", years);
        Integer audits = f.integer("minAudits");
        if (audits != null) n.put("minAudits", audits);
        if (f.str("training") != null) n.put("requiredTraining", f.str("training"));
        if (f.str("criteriaNote") != null) n.put("notes", f.str("criteriaNote"));
        return n;
    }

    private String criteriaSummary(JsonNode c) {
        if (c == null || c.isEmpty()) return "";
        List<String> parts = new ArrayList<>();
        if (c.hasNonNull("minDegreeLevel")) {
            String code = c.get("minDegreeLevel").asText();
            parts.add(lookups.degreeLevels().stream().filter(d -> d.code().equals(code)).map(d -> d.name()).findFirst().orElse(code));
        }
        if (c.hasNonNull("minYearsExperience")) parts.add("≥ " + c.get("minYearsExperience").asText() + " năm KN");
        if (c.hasNonNull("minAudits")) parts.add("≥ " + c.get("minAudits").asText() + " cuộc ĐG");
        if (c.hasNonNull("requiredTraining")) parts.add(c.get("requiredTraining").asText());
        if (c.hasNonNull("notes")) parts.add(c.get("notes").asText());
        return String.join(" · ", parts);
    }

    // ------------------------------------------------------------------ helpers

    private List<Option<UUID>> standardOptions() {
        return lookups.standards().stream()
                .map(s -> new Option<>(s.id(), s.standardCode() + " – " + s.standardName() + "  (" + s.schemeCode() + ")")).toList();
    }

    private List<Option<UUID>> roleOptions() {
        return roles.stream().map(r -> new Option<>(r.id(), r.roleCode() + " – " + r.roleName())).toList();
    }

    private Standard standard(UUID id) {
        return lookups.standards().stream().filter(s -> s.id().equals(id)).findFirst().orElse(null);
    }

    private UUID schemeOf(UUID standardId) {
        Standard s = standard(standardId);
        return s == null ? null : s.schemeId();
    }

    private List<Code> codesOf(UUID standardId) {
        Standard s = standard(standardId);
        return s == null ? List.of() : lookups.codesOfScheme(s.schemeCode());
    }

    private static String indent(Code c) {
        return c.level() > 1 ? "    ".repeat(c.level() - 1) + "↳ " : "";
    }

    private static <T> T value(ComboBox<Option<T>> c) {
        return c.getValue() == null ? null : c.getValue().value();
    }
}
