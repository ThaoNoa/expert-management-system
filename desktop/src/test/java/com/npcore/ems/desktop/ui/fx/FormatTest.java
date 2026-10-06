package com.npcore.ems.desktop.ui.fx;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import javafx.util.StringConverter;
import org.junit.jupiter.api.Test;

class FormatTest {

    private final StringConverter<LocalDate> conv = Form.dateConverter();

    @Test
    void dateConverterAcceptsVietnameseAndIsoFormats() {
        assertThat(conv.fromString("5/10/2026")).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(conv.fromString("05/10/2026")).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(conv.fromString("2026-10-05")).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(conv.fromString("  ")).isNull();
        assertThat(conv.toString(LocalDate.of(2026, 1, 2))).isEqualTo("02/01/2026");
        assertThat(conv.toString(null)).isEmpty();
    }

    @Test
    void fileSizeIsHumanReadable() {
        assertThat(Fmt.size(512)).isEqualTo("512 B");
        assertThat(Fmt.size(2048)).contains("2").endsWith("KB");
        assertThat(Fmt.size(5L * 1024 * 1024)).endsWith("MB");
    }

    @Test
    void toneMapsStatuses() {
        assertThat(Fmt.tone("ACTIVE")).isEqualTo("ok");
        assertThat(Fmt.tone(null)).isEqualTo("muted");
    }
}
