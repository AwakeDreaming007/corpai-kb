package com.xufg.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xufg.common.BizException;
import com.xufg.dto.PermissionResponse;
import com.xufg.dto.RoleCreateRequest;
import com.xufg.dto.RoleListResponse;
import com.xufg.dto.RoleUpdateRequest;
import com.xufg.entity.SysPermission;
import com.xufg.entity.SysRole;
import com.xufg.entity.SysRolePermission;
import com.xufg.entity.SysUserRole;
import com.xufg.mapper.SysPermissionMapper;
import com.xufg.mapper.SysRoleMapper;
import com.xufg.mapper.SysRolePermissionMapper;
import com.xufg.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 系统角色与权限管理服务。
 */
@Service
@RequiredArgsConstructor
public class SysRoleService {

    /** 角色 Mapper。 */
    private final SysRoleMapper sysRoleMapper;

    /** 权限 Mapper。 */
    private final SysPermissionMapper sysPermissionMapper;

    /** 用户角色关联 Mapper。 */
    private final SysUserRoleMapper sysUserRoleMapper;

    /** 角色权限关联 Mapper。 */
    private final SysRolePermissionMapper sysRolePermissionMapper;

    /**
     * 查询角色列表， 并批量组装权限编码。
     */
    @Transactional(readOnly = true)
    public List<RoleListResponse> listRoles() {
        List<SysRole> roles = sysRoleMapper.selectList(Wrappers.emptyWrapper());
        if (roles.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, List<String>> permissionsByRoleId = findPermissionCodesByRoleIds(
                roles.stream().map(SysRole::getId).toList());
        return roles.stream()
                .map(role -> toListResponse(role, permissionsByRoleId.getOrDefault(role.getId(), List.of())))
                .toList();
    }

    /**
     * 创建自定义角色。
     */
    @Transactional
    public Long createRole(RoleCreateRequest request) {
        if (countByRoleCode(request.getRoleCode(), null) > 0) {
            throw new BizException(400, "角色编码已存在");
        }

        SysRole role = new SysRole();
        role.setRoleCode(request.getRoleCode());
        role.setRoleName(request.getRoleName());
        role.setDescription(request.getDescription());
        role.setBuiltIn(false);
        try {
            sysRoleMapper.insert(role);
        } catch (DuplicateKeyException exception) {
            throw new BizException(400, "角色编码已存在");
        }
        return role.getId();
    }

    /**
     * 更新角色信息， 并保护内置角色编码。
     */
    @Transactional
    public void updateRole(Long roleId, RoleUpdateRequest request) {
        SysRole role = requireRole(roleId);
        if (Boolean.TRUE.equals(role.getBuiltIn())) {
            if (!role.getRoleCode().equals(request.getRoleCode())) {
                throw new BizException(400, "内置角色不可修改编码");
            }
            if (!role.getRoleName().equals(request.getRoleName())) {
                throw new BizException(400, "内置角色不可修改名称");
            }
        } else if (!role.getRoleCode().equals(request.getRoleCode())
                && countByRoleCode(request.getRoleCode(), roleId) > 0) {
            throw new BizException(400, "角色编码已存在");
        }

        role.setRoleCode(request.getRoleCode());
        role.setRoleName(request.getRoleName());
        role.setDescription(request.getDescription());
        try {
            sysRoleMapper.updateById(role);
        } catch (DuplicateKeyException exception) {
            throw new BizException(400, "角色编码已存在");
        }
    }

    /**
     * 删除自定义角色， 同一事务清理角色权限。
     */
    @Transactional
    public void deleteRole(Long roleId) {
        SysRole role = requireRole(roleId);
        if (Boolean.TRUE.equals(role.getBuiltIn())) {
            throw new BizException(400, "内置角色不可删除");
        }
        Long boundUserCount = sysUserRoleMapper.selectCount(Wrappers.<SysUserRole>lambdaQuery()
                .eq(SysUserRole::getRoleId, roleId));
        if (boundUserCount != null && boundUserCount > 0) {
            throw new BizException(400, "请先解绑用户");
        }

        sysRolePermissionMapper.delete(Wrappers.<SysRolePermission>lambdaQuery()
                .eq(SysRolePermission::getRoleId, roleId));
        sysRoleMapper.deleteById(roleId);
    }

    /**
     * 重新分配角色权限， 同一事务内先删后插。
     */
    @Transactional
    public void assignPermissions(Long roleId, List<Long> permIds) {
        SysRole role = requireRole(roleId);
        if (Boolean.TRUE.equals(role.getBuiltIn())) {
            throw new BizException(400, "内置角色权限不可修改");
        }

        Set<Long> targetPermIds = new LinkedHashSet<>(permIds);
        if (!targetPermIds.isEmpty() && sysPermissionMapper.selectList(Wrappers.<SysPermission>lambdaQuery()
                        .in(SysPermission::getId, targetPermIds)).size() != targetPermIds.size()) {
            throw new BizException(400, "权限不存在");
        }

        Set<String> operatorPermCodes = currentAuthorityCodes();
        if (!targetPermIds.isEmpty()) {
            List<SysPermission> targetPermissions = sysPermissionMapper.selectList(
                    Wrappers.<SysPermission>lambdaQuery().in(SysPermission::getId, targetPermIds));
            boolean containsUnauthorizedPermission = targetPermissions.stream()
                    .map(SysPermission::getPermCode)
                    .anyMatch(permCode -> !operatorPermCodes.contains(permCode));
            if (containsUnauthorizedPermission) {
                throw new BizException(400, "不能授予自身没有的权限");
            }
        }

        sysRolePermissionMapper.delete(Wrappers.<SysRolePermission>lambdaQuery()
                .eq(SysRolePermission::getRoleId, roleId));
        try {
            targetPermIds.forEach(permId -> {
                SysRolePermission rolePermission = new SysRolePermission();
                rolePermission.setRoleId(roleId);
                rolePermission.setPermId(permId);
                sysRolePermissionMapper.insert(rolePermission);
            });
        } catch (DuplicateKeyException exception) {
            throw new BizException(400, "角色权限已存在");
        }
    }

    /**
     * 查询全量权限项（含 ID 与中文名，供前端权限分配弹窗回显与提交）。
     */
    @Transactional(readOnly = true)
    public List<PermissionResponse> listPermissions() {
        return sysPermissionMapper.selectList(Wrappers.<SysPermission>lambdaQuery()
                        .orderByAsc(SysPermission::getId))
                .stream()
                .map(permission -> {
                    PermissionResponse response = new PermissionResponse();
                    response.setId(permission.getId());
                    response.setPermCode(permission.getPermCode());
                    response.setPermName(permission.getPermName());
                    return response;
                })
                .toList();
    }

    /**
     * 查询角色或返回 404 业务异常。
     */
    private SysRole requireRole(Long roleId) {
        SysRole role = sysRoleMapper.selectById(roleId);
        if (role == null) {
            throw new BizException(404, "角色不存在");
        }
        return role;
    }

    /**
     * 读取当前认证用户的权限码，用于防止 RBAC 垂直越权授权。
     */
    private Set<String> currentAuthorityCodes() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Set.of();
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }

    /**
     * 统计角色编码数量， 可排除当前角色。
     */
    private Long countByRoleCode(String roleCode, Long excludeRoleId) {
        return sysRoleMapper.selectCount(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getRoleCode, roleCode)
                .ne(excludeRoleId != null, SysRole::getId, excludeRoleId));
    }

    /**
     * 批量查询角色权限编码并按角色分组。
     */
    private Map<Long, List<String>> findPermissionCodesByRoleIds(List<Long> roleIds) {
        List<SysRolePermission> rolePermissions = sysRolePermissionMapper.selectList(
                Wrappers.<SysRolePermission>lambdaQuery().in(SysRolePermission::getRoleId, roleIds));
        Set<Long> permissionIds = rolePermissions.stream().map(SysRolePermission::getPermId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (permissionIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, String> permCodeById = sysPermissionMapper.selectList(Wrappers.<SysPermission>lambdaQuery()
                        .in(SysPermission::getId, permissionIds))
                .stream()
                .collect(Collectors.toMap(SysPermission::getId, SysPermission::getPermCode, (left, right) -> left));
        return rolePermissions.stream()
                .collect(Collectors.groupingBy(SysRolePermission::getRoleId,
                        Collectors.mapping(rolePermission -> permCodeById.get(rolePermission.getPermId()),
                                Collectors.toList())));
    }

    /**
     * 转换角色列表响应。
     */
    private RoleListResponse toListResponse(SysRole role, List<String> permissions) {
        RoleListResponse response = new RoleListResponse();
        response.setId(role.getId());
        response.setRoleCode(role.getRoleCode());
        response.setRoleName(role.getRoleName());
        response.setDescription(role.getDescription());
        response.setBuiltIn(role.getBuiltIn());
        response.setPermissions(permissions);
        return response;
    }
}
