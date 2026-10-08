package com.npcore.ems.desktop.ui.fx;

import com.npcore.ems.desktop.api.Dtos.Code;
import com.npcore.ems.desktop.api.Dtos.CodeSet;
import com.npcore.ems.desktop.api.Dtos.Department;
import com.npcore.ems.desktop.api.Dtos.DegreeLevel;
import com.npcore.ems.desktop.api.Dtos.DocumentType;
import com.npcore.ems.desktop.api.Dtos.EducationField;
import com.npcore.ems.desktop.api.Dtos.Industry;
import com.npcore.ems.desktop.api.Dtos.Location;
import com.npcore.ems.desktop.api.Dtos.Standard;
import com.npcore.ems.desktop.api.Session;
import com.npcore.ems.desktop.ui.fx.Form.Option;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Danh mục dùng cho các ô chọn trong form (tải khi mở form, ngoài UI thread). */
public record Lookups(List<Department> departments, List<Location> locations, List<DegreeLevel> degreeLevels,
                      List<EducationField> educationFields, List<Industry> industries, List<Standard> standards,
                      List<DocumentType> documentTypes, List<Code> activeCodes, java.util.Map<UUID, String> schemeOfCodeSet) {

    public static Lookups load(Session s) {
        var api = s.api();
        List<Code> codes = new ArrayList<>();
        java.util.Map<UUID, String> schemes = new java.util.HashMap<>();
        for (CodeSet cs : api.codeSets(null)) {
            if ("ACTIVE".equals(cs.status())) {
                codes.addAll(api.codes(cs.id()));
                schemes.put(cs.id(), cs.schemeCode());
            }
        }
        return new Lookups(api.departments(), api.locations(), api.degreeLevels(), api.educationFields(),
                api.industries(), api.standards(null), api.documentTypes(), codes, schemes);
    }

    public static <T> List<Option<T>> withNone(List<Option<T>> options) {
        List<Option<T>> out = new ArrayList<>();
        out.add(new Option<>(null, "—"));
        out.addAll(options);
        return out;
    }

    public List<Option<UUID>> departmentOptions() {
        return withNone(departments.stream().map(d -> new Option<>(d.id(), d.departmentCode() + " – " + d.departmentName())).toList());
    }

    public List<Option<UUID>> locationOptions() {
        return withNone(locations.stream().map(l -> new Option<>(l.id(),
                l.locationName() + (l.province() == null ? "" : " (" + l.province() + ")"))).toList());
    }

    public List<Option<String>> degreeOptions() {
        return degreeLevels.stream().map(d -> new Option<>(d.code(), d.name())).toList();
    }

    public List<Option<UUID>> fieldOptions() {
        return withNone(educationFields.stream().map(f -> new Option<>(f.id(), f.fieldCode() + " – " + f.fieldName())).toList());
    }

    public List<Option<UUID>> industryOptions() {
        return withNone(industries.stream().map(i -> new Option<>(i.id(), i.industryCode() + " – " + i.industryName())).toList());
    }

    public List<Option<UUID>> standardOptions() {
        return withNone(standards.stream().map(s -> new Option<>(s.id(), s.standardCode() + " – " + s.standardName())).toList());
    }

    public List<Option<String>> documentTypeOptions() {
        return documentTypes.stream().map(t -> new Option<>(t.code(), t.name())).toList();
    }

    public List<Option<UUID>> codeOptions() {
        return activeCodes.stream().map(c -> new Option<>(c.id(),
                schemeOfCodeSet.getOrDefault(c.codeSetId(), "") + " · " + c.codeValue() + " – " + c.codeName())).toList();
    }

    /** Code đang hiệu lực thuộc bộ mã của scheme (theo mã scheme), giữ thứ tự cây. */
    public List<Code> codesOfScheme(String schemeCode) {
        return activeCodes.stream().filter(c -> java.util.Objects.equals(schemeOfCodeSet.get(c.codeSetId()), schemeCode))
                .sorted(java.util.Comparator.comparing(c -> c.path() == null ? c.codeValue() : c.path())).toList();
    }

    public boolean requiresExpiry(String typeCode) {
        return documentTypes.stream().anyMatch(t -> t.code().equals(typeCode) && t.requiresExpiry());
    }
}
