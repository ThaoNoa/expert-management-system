package com.npcore.ems.desktop.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.npcore.ems.desktop.api.Dtos.Experience;
import com.npcore.ems.desktop.api.Dtos.ExperienceRequest;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class JsonTest {

    @Test
    void readsPageOfExperiencesWithIsCurrentFlagAndUnknownFields() throws Exception {
        String json = """
                {"content":[{"id":"8976e93d-f06d-4e00-9d6c-6a1c2b1f0a11","position":"Trưởng phòng",
                  "fromDate":"2020-01-15","isCurrent":true,"years":4.5,"codeIds":[],"unknown":1}],
                 "page":0,"size":20,"totalElements":1,"totalPages":1}""";
        Page<Experience> p = Json.MAPPER.readValue(json, new TypeReference<>() {});
        assertThat(p.totalElements()).isEqualTo(1);
        Experience e = p.content().get(0);
        assertThat(e.isCurrent()).isTrue();
        assertThat(e.fromDate()).isEqualTo(LocalDate.of(2020, 1, 15));
    }

    @Test
    void writesIsoDatesAndIsCurrentName() throws Exception {
        ExperienceRequest r = new ExperienceRequest(null, null, "QA", "NPCore", LocalDate.of(2021, 3, 1), null, true,
                null, null, null, List.of());
        String s = Json.MAPPER.writeValueAsString(r);
        assertThat(s).contains("\"fromDate\":\"2021-03-01\"").contains("\"isCurrent\":true");
    }
}
