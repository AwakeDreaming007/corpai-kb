package com.xufg.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xufg.common.BizException;
import com.xufg.common.UserContext;
import com.xufg.dto.LoginRequest;
import com.xufg.dto.LoginResponse;
import com.xufg.dto.RegisterRequest;
import com.xufg.entity.SysPermission;
import com.xufg.entity.SysRole;
import com.xufg.entity.SysRolePermission;
import com.xufg.entity.SysUser;
import com.xufg.entity.SysUserRole;
import com.xufg.mapper.SysPermissionMapper;
import com.xufg.mapper.SysRoleMapper;
import com.xufg.mapper.SysRolePermissionMapper;
import com.xufg.mapper.SysUserMapper;
import com.xufg.mapper.SysUserRoleMapper;
import com.xufg.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 用户认证与注册服务。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    /** 普通用户角色编码。 */
    private static final String USER_ROLE_CODE = "USER";

    /** 用户 Mapper。 */
    private final SysUserMapper sysUserMapper;

    /** 角色 Mapper。 */
    private final SysRoleMapper sysRoleMapper;

    /** 权限 Mapper。 */
    private final SysPermissionMapper sysPermissionMapper;

    /** 用户角色关联 Mapper。 */
    private final SysUserRoleMapper sysUserRoleMapper;

    /** 角色权限关联 Mapper。 */
    private final SysRolePermissionMapper sysRolePermissionMapper;

    /** JWT 工具。 */
    private final JwtUtil jwtUtil;

    /** 密码加密器。 */
    private final PasswordEncoder passwordEncoder;

    /**
     * 注册用户并绑定 USER 角色。
     */
    @Transactional
    public Long register(RegisterRequest request) {
        Long existsCount = sysUserMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, request.getUsername()));
        if (existsCount != null && existsCount > 0) {
            throw new BizException(400, "用户名已存在");
        }

        SysRole userRole = sysRoleMapper.selectOne(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getRoleCode, USER_ROLE_CODE));
        if (userRole == null) {
            throw new BizException(500, "USER 角色不存在");
        }

        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setStatus(1);
        try {
            sysUserMapper.insert(user);
            SysUserRole userRoleRelation = new SysUserRole();
            userRoleRelation.setUserId(user.getId());
            userRoleRelation.setRoleId(userRole.getId());
            sysUserRoleMapper.insert(userRoleRelation);
        } catch (DuplicateKeyException exception) {
            throw new BizException(400, "用户名已存在");
        }
        return user.getId();
    }

    /**
     * 校验账号密码并签发 JWT。
     */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        SysUser user = sysUserMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, request.getUsername()));
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BizException(401, "用户名或密码错误");
        }
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            throw new BizException(401, "账号已被禁用");
        }

        List<String> roles = listRoleCodes(user.getId());
        List<String> permissions = listPermissionCodes(user.getId());
        List<String> authorities = new ArrayList<>(roles.size() + permissions.size());
        authorities.addAll(roles);
        authorities.addAll(permissions);

        LoginResponse response = new LoginResponse();
        response.setToken(jwtUtil.generateToken(
                user.getId(), user.getUsername(), user.getNickname(), authorities));
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        response.setRoles(roles);
        response.setPermissions(permissions);
        return response;
    }

    /**
     * 实时查询当前登录用户信息。
     */
    @Transactional(readOnly = true)
    public LoginResponse me() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(401, "用户不存在");
        }

        LoginResponse response = new LoginResponse();
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        response.setRoles(listRoleCodes(user.getId()));
        response.setPermissions(listPermissionCodes(user.getId()));
        return response;
    }

    /**
     * 批量查询用户角色编码。
     */
    private List<String> listRoleCodes(Long userId) {
        List<SysUserRole> userRoles = sysUserRoleMapper.selectList(Wrappers.<SysUserRole>lambdaQuery()
                .eq(SysUserRole::getUserId, userId));
        List<Long> roleIds = userRoles.stream().map(SysUserRole::getRoleId).toList();
        if (roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        return sysRoleMapper.selectList(Wrappers.<SysRole>lambdaQuery()
                        .in(SysRole::getId, roleIds))
                .stream()
                .map(SysRole::getRoleCode)
                .toList();
    }

    /**
     * 批量查询角色对应的权限编码。
     */
    private List<String> listPermissionCodes(Long userId) {
        List<SysUserRole> userRoles = sysUserRoleMapper.selectList(Wrappers.<SysUserRole>lambdaQuery()
                .eq(SysUserRole::getUserId, userId));
        List<Long> roleIds = userRoles.stream().map(SysUserRole::getRoleId).toList();
        if (roleIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<SysRolePermission> rolePermissions = sysRolePermissionMapper.selectList(
                Wrappers.<SysRolePermission>lambdaQuery().in(SysRolePermission::getRoleId, roleIds));
        Set<Long> permissionIds = new LinkedHashSet<>();
        rolePermissions.forEach(rolePermission -> permissionIds.add(rolePermission.getPermId()));
        if (permissionIds.isEmpty()) {
            return Collections.emptyList();
        }

        return sysPermissionMapper.selectList(Wrappers.<SysPermission>lambdaQuery()
                        .in(SysPermission::getId, permissionIds))
                .stream()
                .map(SysPermission::getPermCode)
                .toList();
    }
}
