package com.npcore.ems.shared.importing;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/** CSV UTF-8 (có/không BOM), phân tách bằng dấu phẩy hoặc chấm phẩy (Excel VN hay dùng ';'), hỗ trợ ngoặc kép. */
@Component
public class CsvTabularReader implements TabularReader {

    @Override
    public boolean supports(String fileName) {
        return fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".csv");
    }

    @Override
    public List<Map<String, String>> read(InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String all = reader.lines().reduce(new StringBuilder(), (sb, l) -> sb.append(l).append('\n'), StringBuilder::append).toString();
        if (all.startsWith("﻿")) all = all.substring(1);
        List<List<String>> records = parse(all);
        if (records.isEmpty()) return List.of();
        List<String> header = records.get(0).stream().map(h -> h.trim().toLowerCase(Locale.ROOT)).toList();
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 1; i < records.size(); i++) {
            List<String> rec = records.get(i);
            if (rec.stream().allMatch(String::isBlank)) {
                rows.add(Map.of());           // giữ vị trí dòng để báo lỗi đúng số dòng
                continue;
            }
            Map<String, String> row = new LinkedHashMap<>();
            for (int c = 0; c < header.size(); c++) {
                row.put(header.get(c), c < rec.size() ? rec.get(c).trim() : "");
            }
            rows.add(row);
        }
        return rows;
    }

    static List<List<String>> parse(String text) {
        char sep = detectSeparator(text);
        List<List<String>> out = new ArrayList<>();
        List<String> current = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == sep) {
                current.add(field.toString());
                field.setLength(0);
            } else if (ch == '\n') {
                current.add(field.toString());
                field.setLength(0);
                out.add(current);
                current = new ArrayList<>();
            } else if (ch != '\r') {
                field.append(ch);
            }
        }
        if (field.length() > 0 || !current.isEmpty()) {
            current.add(field.toString());
            out.add(current);
        }
        return out;
    }

    private static char detectSeparator(String text) {
        int nl = text.indexOf('\n');
        String first = nl >= 0 ? text.substring(0, nl) : text;
        return first.chars().filter(c -> c == ';').count() > first.chars().filter(c -> c == ',').count() ? ';' : ',';
    }
}
