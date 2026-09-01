package com.xufg.controller;

import com.xufg.common.Result;
import com.xufg.dto.PermissionResponse;
import com.xufg.dto.RoleCreateRequest;
import com.xufg.dto.RoleListResponse;
import com.xufg.dto.RolePermissionAssignRequest;
import com.xufg.dto.RoleUpdateRequest;
import com.xufg.service.SysRoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 系统角色与权限管理接口。
 */
@RestController
@RequestMapping("/api/sys")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('sys:role:manage')")
public class SysRoleController {

    /** 角色与权限管理服务。 */
    private final SysRoleService sysRoleService;

    /**
     * 查询角色列表。
     */
    @GetMapping("/roles")
    public Result<List<RoleListResponse>> listRoles() {
        return Result.ok(sysRoleService.listRoles());
    }

    /**
     * 创建自定义角色。
     */
    @PostMapping("/roles")
    public Result<Long> createRole(@Valid @RequestBody RoleCreateRequest request) {
        return Result.ok(sysRoleService.createRole(request));
    }

    /**
     * 修改角色信息。
     */
    @PutMapping("/roles/{id}")
    public Result<Void> updateRole(@PathVariable Long id, @Valid @RequestBody RoleUpdateRequest request) {
        sysRoleService.updateRole(id, request);
        return Result.ok();
    }

    /**
     * 删除自定义角色。
     */
    @DeleteMapping("/roles/{id}")
    public Result<Void> deleteRole(@PathVariable Long id) {
        sysRoleService.deleteRole(id);
        return Result.ok();
    }

    /**
     * 重新分配角色权限。
     */
    @PutMapping("/roles/{id}/permissions")
    public Result<Void> assignPermissions(@PathVariable Long id,
                                          @Valid @RequestBody RolePermissionAssignRequest request) {
        sysRoleService.assignPermissions(id, request.getPermIds());
        return Result.ok();
    }

    /**
     * 查询全量权限项（含 ID 与中文名）。
     */
    @GetMapping("/permissions")
    public Result<List<PermissionResponse>> listPermissions() {
        return Result.ok(sysRoleService.listPermissions());
    }
}
