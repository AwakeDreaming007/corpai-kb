package com.xufg.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xufg.common.BizException;
import com.xufg.common.UserContext;
import com.xufg.dto.UserListResponse;
import com.xufg.entity.SysRole;
import com.xufg.entity.SysUser;
import com.xufg.entity.SysUserRole;
import com.xufg.mapper.SysRoleMapper;
import com.xufg.mapper.SysUserMapper;
import com.xufg.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 系统用户管理服务。
 */
@Service
@RequiredArgsConstructor
public class SysUserService {

    /** 用户 Mapper。 */
    private final SysUserMapper sysUserMapper;

    /** 角色 Mapper。 */
    private final SysRoleMapper sysRoleMapper;

    /** 用户角色关联 Mapper。 */
    private final SysUserRoleMapper sysUserRoleMapper;

    /**
     * 分页查询用户列表， 并批量组装角色编码。
     */
    @Transactional(readOnly = true)
    public Page<UserListResponse> listUsers(Integer page, Integer size, String keyword) {
        long current = page == null ? 1L : Math.max(1, page);
        long pageSize = size == null ? 10L : Math.min(100L, Math.max(1, size));
        Page<SysUser> userPage = sysUserMapper.selectPage(new Page<>(current, pageSize), buildUserQuery(keyword));
        List<SysUser> users = userPage.getRecords();
        Map<Long, List<String>> roleCodesByUserId = users.isEmpty()
                ? Collections.emptyMap()
                : findRoleCodesByUserIds(users.stream().map(SysUser::getId).toList());

        List<UserListResponse> records = users.stream()
                .map(user -> toListResponse(user, roleCodesByUserId.getOrDefault(user.getId(), List.of())))
                .toList();
        Page<UserListResponse> responsePage = new Page<>(userPage.getCurrent(), userPage.getSize(), userPage.getTotal());
        responsePage.setPages(userPage.getPages());
        responsePage.setRecords(records);
        return responsePage;
    }

    /**
     * 更新用户启用状态， 禁止用户禁用自己。
     */
    @Transactional
    public void updateStatus(Long userId, Integer status) {
        if (userId.equals(UserContext.getUserId())) {
            throw new BizException(400, "不能禁用当前登录用户");
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(404, "用户不存在");
        }
        user.setStatus(status);
        sysUserMapper.updateById(user);
    }

    /**
     * 重新分配用户角色， 同一事务内先删后插。
     */
    @Transactional
    public void assignRoles(Long userId, List<Long> roleIds) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(404, "用户不存在");
        }
        Set<Long> targetRoleIds = new LinkedHashSet<>(roleIds);
        if (!targetRoleIds.isEmpty()) {
            List<SysRole> targetRoles = sysRoleMapper.selectList(Wrappers.<SysRole>lambdaQuery()
                    .in(SysRole::getId, targetRoleIds));
            if (targetRoles.size() != targetRoleIds.size()) {
                throw new BizException(400, "角色不存在");
            }

            Set<String> operatorRoleCodes = currentRoleCodes();
            boolean containsAdminRole = targetRoles.stream()
                    .anyMatch(role -> "ADMIN".equals(role.getRoleCode()));
            if (containsAdminRole) {
                throw new BizException(400, "不可分配 ADMIN 角色");
            }
            // ADMIN 是授权范围特权角色，可以分配普通及自定义角色；其他操作者只能授予自身持有的角色。
            boolean operatorIsAdmin = operatorRoleCodes.contains("ADMIN");
            boolean containsUnauthorizedRole = !operatorIsAdmin && targetRoles.stream()
                    .map(SysRole::getRoleCode)
                    .anyMatch(roleCode -> !operatorRoleCodes.contains(roleCode));
            if (containsUnauthorizedRole) {
                throw new BizException(400, "不能授予自身没有的角色");
            }
        }

        sysUserRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, userId));
        try {
            targetRoleIds.forEach(roleId -> {
                SysUserRole userRole = new SysUserRole();
                userRole.setUserId(userId);
                userRole.setRoleId(roleId);
                sysUserRoleMapper.insert(userRole);
            });
        } catch (DuplicateKeyException exception) {
            throw new BizException(400, "角色分配冲突，请刷新后重试");
        }
    }

    /**
     * 读取当前认证用户持有的角色码，防止角色分配形成垂直越权。
     */
    private Set<String> currentRoleCodes() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Set.of();
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }

    /**
     * 构建用户关键字过滤条件。
     */
    private Wrapper<SysUser> buildUserQuery(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return Wrappers.emptyWrapper();
        }
        return Wrappers.<SysUser>lambdaQuery()
                .like(SysUser::getUsername, keyword)
                .or()
                .like(SysUser::getNickname, keyword);
    }

    /**
     * 批量查询用户角色编码并按用户分组。
     */
    private Map<Long, List<String>> findRoleCodesByUserIds(List<Long> userIds) {
        List<SysUserRole> userRoles = sysUserRoleMapper.selectList(Wrappers.<SysUserRole>lambdaQuery()
                .in(SysUserRole::getUserId, userIds));
        Set<Long> roleIds = userRoles.stream().map(SysUserRole::getRoleId).collect(Collectors.toCollection(LinkedHashSet::new));
        if (roleIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, String> roleCodeById = sysRoleMapper.selectList(Wrappers.<SysRole>lambdaQuery()
                        .in(SysRole::getId, roleIds))
                .stream()
                .collect(Collectors.toMap(SysRole::getId, SysRole::getRoleCode, (left, right) -> left));
        return userRoles.stream()
                .collect(Collectors.groupingBy(SysUserRole::getUserId,
                        Collectors.mapping(userRole -> roleCodeById.get(userRole.getRoleId()), Collectors.toList())));
    }

    /**
     * 转换用户列表响应。
     */
    private UserListResponse toListResponse(SysUser user, List<String> roles) {
        UserListResponse response = new UserListResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        response.setStatus(user.getStatus());
        response.setRoles(roles);
        return response;
    }
}
