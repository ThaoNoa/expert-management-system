package com.npcore.ems.shared.importing;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Đọc file bảng (CSV / XLSX) thành danh sách dòng: key = tên cột (lower-case, trim), value = chuỗi.
 * Dòng đầu tiên là header. Số thứ tự dòng dữ liệu bắt đầu từ 2 (khớp với Excel).
 */
public interface TabularReader {
    boolean supports(String fileName);

    List<Map<String, String>> read(InputStream in) throws IOException;
}
