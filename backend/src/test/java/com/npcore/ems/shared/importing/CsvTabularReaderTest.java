package com.npcore.ems.shared.importing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CsvTabularReaderTest {

    private final CsvTabularReader reader = new CsvTabularReader();

    private List<Map<String, String>> read(String csv) throws IOException {
        return reader.read(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void supportsOnlyCsvExtension() {
        assertThat(reader.supports("codes.CSV")).isTrue();
        assertThat(reader.supports("codes.xlsx")).isFalse();
        assertThat(reader.supports(null)).isFalse();
    }

    @Test
    void headerIsTrimmedAndLowerCasedAndValuesTrimmed() throws IOException {
        var rows = read(" Code_Value , CODE_NAME \nA ,  Alpha \n");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("code_value", "A").containsEntry("code_name", "Alpha");
    }

    @Test
    void quotedFieldsWithSeparatorEscapedQuoteAndNewline() throws IOException {
        var rows = read("a,b,c\n\"x, y\",\"He said \"\"hi\"\"\",\"line1\nline2\"\n");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("a")).isEqualTo("x, y");
        assertThat(rows.get(0).get("b")).isEqualTo("He said \"hi\"");
        assertThat(rows.get(0).get("c")).isEqualTo("line1\nline2");
    }

    @Test
    void semicolonSeparatorIsDetected() throws IOException {
        var rows = read("code_value;code_name;parent_code\nA1;Tên, có phẩy;A\n");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("code_value", "A1")
                .containsEntry("code_name", "Tên, có phẩy")
                .containsEntry("parent_code", "A");
    }

    @Test
    void utf8BomIsStripped() throws IOException {
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = "code_value,code_name\nA,Ăn uống\n".getBytes(StandardCharsets.UTF_8);
        byte[] all = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, all, 0, bom.length);
        System.arraycopy(body, 0, all, bom.length, body.length);
        var rows = reader.read(new ByteArrayInputStream(all));
        assertThat(rows.get(0).keySet()).containsExactly("code_value", "code_name");
        assertThat(rows.get(0).get("code_name")).isEqualTo("Ăn uống");
    }

    @Test
    void blankRowsKeepTheirPositionSoRowNumbersStayCorrect() throws IOException {
        // dòng Excel: 1 = header, 2 = A, 3 = trống, 4 = ;; (trống), 5 = B
        var rows = read("code_value,code_name\r\nA,Alpha\r\n\r\n , \r\nB,Beta\r\n");
        assertThat(rows).hasSize(4);
        assertThat(rows.get(0)).containsEntry("code_value", "A");
        assertThat(rows.get(1)).isEmpty();
        assertThat(rows.get(2)).isEmpty();
        assertThat(rows.get(3)).containsEntry("code_value", "B");     // index 3 → dòng 5
    }

    @Test
    void missingTrailingColumnsBecomeEmptyAndNoFinalNewlineIsFine() throws IOException {
        var rows = read("a,b,c\n1");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("a", "1").containsEntry("b", "").containsEntry("c", "");
    }

    @Test
    void emptyInputGivesNoRows() throws IOException {
        assertThat(read("")).isEmpty();
        assertThat(read("a,b\n")).isEmpty();
    }
}
