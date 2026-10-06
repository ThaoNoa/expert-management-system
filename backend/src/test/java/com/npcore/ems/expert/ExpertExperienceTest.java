package com.npcore.ems.expert;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ExpertExperienceTest {

    private static ExpertExperience exp(LocalDate from, LocalDate to, LocalDate verifiedUntil) {
        ExpertExperience e = new ExpertExperience();
        e.setFromDate(from);
        e.setToDate(to);
        e.setVerifiedUntil(verifiedUntil);
        e.setCurrent(to == null);
        return e;
    }

    @Test
    void finishedExperienceCountsUntilToDate() {
        assertThat(exp(LocalDate.of(2015, 1, 1), LocalDate.of(2018, 1, 1), null).years()).isEqualTo(3.0);
        assertThat(exp(LocalDate.of(2015, 1, 1), LocalDate.of(2016, 7, 1), null).years()).isEqualTo(1.5);
    }

    @Test
    void toDateWinsOverVerifiedUntil() {
        assertThat(exp(LocalDate.of(2015, 1, 1), LocalDate.of(2017, 1, 1), LocalDate.of(2020, 1, 1)).years())
                .isEqualTo(2.0);
    }

    @Test
    void currentExperienceCountsOnlyUntilVerifiedDateAndDoesNotGrow() {
        ExpertExperience e = exp(LocalDate.of(2010, 1, 1), null, LocalDate.of(2020, 1, 1));
        assertThat(e.years()).isEqualTo(10.0);                 // không phụ thuộc ngày hiện tại
    }

    @Test
    void noEndOrEndBeforeStartGivesZero() {
        assertThat(exp(LocalDate.of(2010, 1, 1), null, null).years()).isZero();
        assertThat(exp(LocalDate.of(2010, 1, 1), null, LocalDate.of(2009, 1, 1)).years()).isZero();
        assertThat(exp(LocalDate.of(2010, 1, 1), LocalDate.of(2010, 1, 1), null).years()).isZero();
    }

    @Test
    void roundedToOneDecimal() {
        // 400 ngày / 365.25 = 1.095 → 1.1
        LocalDate from = LocalDate.of(2020, 1, 1);
        assertThat(exp(from, from.plusDays(400), null).years()).isEqualTo(1.1);
    }
}
