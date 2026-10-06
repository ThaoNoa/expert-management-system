package com.npcore.ems.shared.importing;

import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/** Đọc sheet đầu tiên của file .xlsx. Ô ngày được chuẩn hoá thành yyyy-MM-dd. */
@Component
public class PoiExcelTabularReader implements TabularReader {

    private final DataFormatter formatter = new DataFormatter(Locale.ROOT);

    @Override
    public boolean supports(String fileName) {
        return fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".xlsx");
    }

    @Override
    public List<Map<String, String>> read(InputStream in) throws IOException {
        try (Workbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = wb.getSheetAt(0);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) return List.of();
            List<String> header = new ArrayList<>();
            for (Cell c : headerRow) header.add(formatter.formatCellValue(c).trim().toLowerCase(Locale.ROOT));
            List<Map<String, String>> rows = new ArrayList<>();
            for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                Map<String, String> values = new LinkedHashMap<>();
                boolean any = false;
                for (int c = 0; c < header.size(); c++) {
                    String v = row == null ? "" : cellText(row.getCell(c));
                    any |= !v.isBlank();
                    values.put(header.get(c), v);
                }
                rows.add(any ? values : Map.of());
            }
            return rows;
        }
    }

    private String cellText(Cell cell) {
        if (cell == null) return "";
        if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
        return formatter.formatCellValue(cell).trim();
    }
}
