package com.npcore.ems.desktop.ui.master;

import com.npcore.ems.desktop.api.Dtos.Activity;
import com.npcore.ems.desktop.api.Dtos.AssessmentRole;
import com.npcore.ems.desktop.api.Dtos.Department;
import com.npcore.ems.desktop.api.Dtos.EducationField;
import com.npcore.ems.desktop.api.Dtos.Industry;
import com.npcore.ems.desktop.api.Dtos.Location;
import com.npcore.ems.desktop.api.Dtos.Scheme;
import com.npcore.ems.desktop.api.Session;
import com.npcore.ems.desktop.ui.fx.Fmt;
import com.npcore.ems.desktop.ui.fx.Form;
import com.npcore.ems.desktop.ui.fx.Tables;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.scene.Node;

/** Cấu hình các danh mục đơn giản (FR-1.4, 1.5, 3.3, 3.4, 3.5, BR-2.2.1). */
public final class CatalogViews {
    private CatalogViews() {}

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private static final List<String> ACTIVE_INACTIVE = List.of("ACTIVE", "INACTIVE");

    public static Node departments(Session s) {
        return new CatalogView<>(s, new CatalogView.Spec<Department>("Phòng ban", "departments", "phòng ban",
                () -> s.api().departments(),
                List.of(Tables.col("Mã", Department::departmentCode, 120), Tables.col("Tên phòng ban", Department::departmentName, 320),
                        Tables.status("Trạng thái", Department::status, 140)),
                Department::id, d -> d.departmentCode() + " " + d.departmentName(),
                d -> {
                    Form f = new Form().text("code", "Mã", true).text("name", "Tên phòng ban", true)
                            .codes("status", "Trạng thái", ACTIVE_INACTIVE, true).set("status", "ACTIVE");
                    if (d != null) f.set("code", d.departmentCode()).set("name", d.departmentName()).set("status", d.status());
                    return f;
                },
                f -> map("departmentCode", f.str("code"), "departmentName", f.str("name"), "status", f.value("status"))));
    }

    public static Node assessmentRoles(Session s) {
        return new CatalogView<>(s, new CatalogView.Spec<AssessmentRole>("Vai trò đánh giá", "assessment-roles", "vai trò",
                () -> s.api().assessmentRoles(),
                List.of(Tables.col("Mã", AssessmentRole::roleCode, 90), Tables.col("Tên vai trò", AssessmentRole::roleName, 260),
                        Tables.col("Tính coverage", r -> r.countsForCoverage() ? "✓" : "", 110),
                        Tables.col("Thứ tự", AssessmentRole::sortOrder, 70), Tables.status("Trạng thái", AssessmentRole::status, 140)),
                AssessmentRole::id, r -> r.roleCode() + " " + r.roleName(),
                r -> {
                    Form f = new Form().text("code", "Mã (LA, AU, TE…)", true).text("name", "Tên vai trò", true)
                            .area("description", "Mô tả", false).check("coverage", "Tính vào coverage đoàn")
                            .text("order", "Thứ tự hiển thị", false)
                            .codes("status", "Trạng thái", ACTIVE_INACTIVE, true).set("status", "ACTIVE").set("coverage", true);
                    if (r != null) {
                        f.set("code", r.roleCode()).set("name", r.roleName()).set("description", r.description())
                                .set("coverage", r.countsForCoverage()).set("order", r.sortOrder()).set("status", r.status());
                    }
                    return f;
                },
                f -> map("roleCode", f.str("code"), "roleName", f.str("name"), "description", f.str("description"),
                        "countsForCoverage", f.bool("coverage"), "sortOrder", f.integer("order") == null ? 0 : f.integer("order"),
                        "status", f.value("status"))));
    }

    public static Node schemes(Session s) {
        return new CatalogView<>(s, new CatalogView.Spec<Scheme>("Scheme (chương trình chứng nhận)", "schemes", "scheme",
                () -> s.api().schemes(),
                List.of(Tables.col("Mã", Scheme::schemeCode, 120), Tables.col("Tên", Scheme::schemeName, 280),
                        Tables.col("Code cha bao code con", sc -> sc.parentCoversChild() ? "Có" : "Không", 170),
                        Tables.status("Trạng thái", Scheme::status, 140)),
                Scheme::id, sc -> sc.schemeCode() + " " + sc.schemeName(),
                sc -> {
                    Form f = new Form().text("code", "Mã scheme", true).text("name", "Tên", true)
                            .area("description", "Mô tả", false)
                            .check("parentCoversChild", "Code cha bao phủ code con (BR-3.1.3)")
                            .codes("status", "Trạng thái", ACTIVE_INACTIVE, true).set("status", "ACTIVE");
                    if (sc != null) {
                        f.set("code", sc.schemeCode()).set("name", sc.schemeName()).set("description", sc.description())
                                .set("parentCoversChild", sc.parentCoversChild()).set("status", sc.status());
                    }
                    return f;
                },
                f -> map("schemeCode", f.str("code"), "schemeName", f.str("name"), "description", f.str("description"),
                        "parentCoversChild", f.bool("parentCoversChild"), "status", f.value("status"))));
    }

    public static Node industries(Session s) {
        return new CatalogView<>(s, new CatalogView.Spec<Industry>("Ngành", "industries", "ngành",
                () -> s.api().industries(),
                List.of(Tables.col("Mã", Industry::industryCode, 120), Tables.col("Tên ngành", Industry::industryName, 300),
                        Tables.col("Mô tả", Industry::description, 300)),
                Industry::id, i -> i.industryCode() + " " + i.industryName(),
                i -> {
                    Form f = new Form().text("code", "Mã", true).text("name", "Tên ngành", true).area("description", "Mô tả", false);
                    if (i != null) f.set("code", i.industryCode()).set("name", i.industryName()).set("description", i.description());
                    return f;
                },
                f -> map("industryCode", f.str("code"), "industryName", f.str("name"), "description", f.str("description"))));
    }

    public static Node activities(Session s) {
        return new CatalogView<>(s, new CatalogView.Spec<Activity>("Hoạt động", "activities", "hoạt động",
                () -> s.api().list("activities", new com.fasterxml.jackson.core.type.TypeReference<List<Activity>>() {}),
                List.of(Tables.col("Mã", Activity::activityCode, 120), Tables.col("Tên hoạt động", Activity::activityName, 300),
                        Tables.col("Mô tả", Activity::description, 300)),
                Activity::id, a -> a.activityCode() + " " + a.activityName(),
                a -> {
                    Form f = new Form().text("code", "Mã", true).text("name", "Tên hoạt động", true).area("description", "Mô tả", false);
                    if (a != null) f.set("code", a.activityCode()).set("name", a.activityName()).set("description", a.description());
                    return f;
                },
                f -> map("activityCode", f.str("code"), "activityName", f.str("name"), "description", f.str("description"))));
    }

    public static Node locations(Session s) {
        return new CatalogView<>(s, new CatalogView.Spec<Location>("Địa điểm", "locations", "địa điểm",
                () -> s.api().locations(),
                List.of(Tables.col("Tên địa điểm", Location::locationName, 220), Tables.col("Tỉnh/TP", Location::province, 140),
                        Tables.col("Vùng", Location::region, 100), Tables.col("Quốc gia", Location::country, 80),
                        Tables.col("Vĩ độ", l -> Fmt.text(l.latitude()), 100), Tables.col("Kinh độ", l -> Fmt.text(l.longitude()), 100)),
                Location::id, l -> l.locationName() + " " + (l.province() == null ? "" : l.province()),
                l -> {
                    Form f = new Form().text("name", "Tên địa điểm", true).text("province", "Tỉnh/TP", false)
                            .codes("region", "Vùng", List.of("Bắc", "Trung", "Nam"), false)
                            .text("country", "Quốc gia (mã 2 ký tự)", false).text("lat", "Vĩ độ", false).text("lng", "Kinh độ", false)
                            .set("country", "VN");
                    if (l != null) {
                        f.set("name", l.locationName()).set("province", l.province()).set("region", l.region())
                                .set("country", l.country()).set("lat", l.latitude()).set("lng", l.longitude());
                    }
                    return f;
                },
                f -> map("locationName", f.str("name"), "province", f.str("province"), "region", f.value("region"),
                        "country", f.str("country") == null ? "VN" : f.str("country").toUpperCase(),
                        "latitude", f.decimal("lat"), "longitude", f.decimal("lng"))));
    }

    public static Node educationFields(Session s) {
        return new CatalogView<>(s, new CatalogView.Spec<EducationField>("Lĩnh vực đào tạo", "education-fields", "lĩnh vực",
                () -> s.api().educationFields(),
                List.of(Tables.col("Mã", EducationField::fieldCode, 120), Tables.col("Tên lĩnh vực", EducationField::fieldName, 360)),
                EducationField::id, e -> e.fieldCode() + " " + e.fieldName(),
                e -> {
                    Form f = new Form().text("code", "Mã", true).text("name", "Tên lĩnh vực", true);
                    if (e != null) f.set("code", e.fieldCode()).set("name", e.fieldName());
                    return f;
                },
                f -> map("fieldCode", f.str("code"), "fieldName", f.str("name"))));
    }
}
