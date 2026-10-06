package com.npcore.ems.shared.importing;

import com.npcore.ems.shared.web.ApiException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** Chọn reader theo phần mở rộng file. Build offline không có POI thì chỉ hỗ trợ CSV. */
@Component
@RequiredArgsConstructor
public class TabularFileParser {

    private final List<TabularReader> readers;

    public List<Map<String, String>> parse(MultipartFile file) {
        TabularReader reader = readers.stream().filter(r -> r.supports(file.getOriginalFilename())).findFirst()
                .orElseThrow(() -> ApiException.badRequest("Định dạng file không hỗ trợ (dùng .xlsx hoặc .csv)"));
        try (InputStream in = file.getInputStream()) {
            return reader.read(in);
        } catch (IOException e) {
            throw ApiException.badRequest("Không đọc được file: " + e.getMessage());
        }
    }
}
