package com.xufg.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xufg.common.Result;
import com.xufg.dto.UserListResponse;
import com.xufg.dto.UserStatusRequest;
import com.xufg.dto.UserRoleAssignRequest;
import com.xufg.service.SysUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 系统用户管理接口。
 */
@RestController
@RequestMapping("/api/sys/users")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('sys:user:manage')")
public class SysUserController {

    /** 用户管理服务。 */
    private final SysUserService sysUserService;

    /**
     * 分页查询用户列表。
     */
    @GetMapping
    public Result<Page<UserListResponse>> listUsers(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String keyword) {
        return Result.ok(sysUserService.listUsers(page, size, keyword));
    }

    /**
     * 启用或禁用用户。
     */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody UserStatusRequest request) {
        sysUserService.updateStatus(id, request.getStatus());
        return Result.ok();
    }

    /**
     * 重新分配用户角色。
     */
    @PutMapping("/{id}/roles")
    public Result<Void> assignRoles(@PathVariable Long id, @Valid @RequestBody UserRoleAssignRequest request) {
        sysUserService.assignRoles(id, request.getRoleIds());
        return Result.ok();
    }
}
