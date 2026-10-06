package com.npcore.ems.shared.settings;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('SETTING_MANAGE')")
public class SettingsController {

    private final SettingsService service;

    public record SettingDto(String key, JsonNode value, String valueType, String category, String description) {
        static SettingDto from(SystemSetting s) {
            return new SettingDto(s.getKey(), s.getValue(), s.getValueType(), s.getCategory(), s.getDescription());
        }
    }

    public record UpdateRequest(@NotNull JsonNode value) {}

    @GetMapping
    public List<SettingDto> list() {
        return service.findAll().stream().map(SettingDto::from).toList();
    }

    @PutMapping("/{key}")
    public SettingDto update(@PathVariable String key, @RequestBody @jakarta.validation.Valid UpdateRequest req) {
        return SettingDto.from(service.update(key, req.value()));
    }
}
