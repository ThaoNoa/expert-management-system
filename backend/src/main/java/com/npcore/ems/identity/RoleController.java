package com.npcore.ems.identity;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('USER_MANAGE')")
public class RoleController {

    private final RoleService service;

    @GetMapping("/roles")
    public List<RoleService.RoleDto> list() {
        return service.list();
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    public RoleService.RoleDto create(@RequestBody @Valid RoleService.RoleRequest req) {
        return service.create(req);
    }

    @PutMapping("/roles/{id}")
    public RoleService.RoleDto update(@PathVariable UUID id, @RequestBody @Valid RoleService.RoleRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/roles/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @GetMapping("/permissions")
    public List<RoleService.PermissionDto> permissions() {
        return service.permissions();
    }
}
