package com.npcore.ems.shared.web;

import java.util.ArrayList;
import java.util.List;

public record ImportResult(int total, int imported, int skipped, List<RowError> errors) {

    public record RowError(int row, String message) {}

    /** Bộ đếm có thể thay đổi, dùng trong lúc import rồi đóng băng bằng {@link #build()}. */
    public static final class Builder {
        private int total;
        private int imported;
        private int skipped;
        private final List<RowError> errors = new ArrayList<>();

        public void row() { total++; }
        public void imported() { imported++; }
        public void skipped() { skipped++; }
        public void error(int row, String message) { errors.add(new RowError(row, message)); }
        public boolean hasErrors() { return !errors.isEmpty(); }
        public ImportResult build() { return new ImportResult(total, imported, skipped, List.copyOf(errors)); }
    }
}
