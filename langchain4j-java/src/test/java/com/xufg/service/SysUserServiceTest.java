package com.xufg.service;

import com.xufg.common.BizException;
import com.xufg.common.UserContext;
import com.xufg.entity.SysRole;
import com.xufg.entity.SysUser;
import com.xufg.entity.SysUserRole;
import com.xufg.mapper.SysRoleMapper;
import com.xufg.mapper.SysUserMapper;
import com.xufg.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.AfterEach;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * 系统用户管理服务单元测试。
 */
@ExtendWith(MockitoExtension.class)
class SysUserServiceTest {

    /** 用户 Mapper。 */
    @Mock
    private SysUserMapper sysUserMapper;

    /** 角色 Mapper。 */
    @Mock
    private SysRoleMapper sysRoleMapper;

    /** 用户角色关联 Mapper。 */
    @Mock
    private SysUserRoleMapper sysUserRoleMapper;

    /** 被测用户服务。 */
    @InjectMocks
    private SysUserService sysUserService;

    /**
     * 清理当前用户上下文。
     */
    @AfterEach
    void tearDown() {
        UserContext.clear();
        SecurityContextHolder.clearContext();
    }

    /**
     * 校验用户不能禁用自己。
     */
    @Test
    void shouldRejectDisablingSelf() {
        UserContext.set(1L, "tester");

        BizException exception = assertThrows(BizException.class,
                () -> sysUserService.updateStatus(1L, 0));

        assertEquals("不能禁用当前登录用户", exception.getMessage());
        verifyNoInteractions(sysUserMapper, sysRoleMapper, sysUserRoleMapper);
    }

    /**
     * 校验角色不存在时不执行删除或插入。
     */
    @Test
    void shouldRejectAssigningUnknownRole() {
        SysUser user = new SysUser();
        user.setId(2L);
        when(sysUserMapper.selectById(2L)).thenReturn(user);
        when(sysRoleMapper.selectList(any())).thenReturn(List.of());

        BizException exception = assertThrows(BizException.class,
                () -> sysUserService.assignRoles(2L, List.of(3L)));

        assertEquals("角色不存在", exception.getMessage());
        verifyNoInteractions(sysUserRoleMapper);
    }

    /**
     * 校验角色分配按先删后插执行。
     */
    @Test
    void shouldAssignRolesAfterRemovingOldRelations() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("manager", "n",
                        List.of(new SimpleGrantedAuthority("EDITOR"))));
        SysUser user = new SysUser();
        user.setId(2L);
        SysRole role = new SysRole();
        role.setId(3L);
        role.setRoleCode("EDITOR");
        when(sysUserMapper.selectById(2L)).thenReturn(user);
        when(sysRoleMapper.selectList(any())).thenReturn(List.of(role));

        sysUserService.assignRoles(2L, List.of(3L));

        verify(sysUserRoleMapper).delete(any());
        verify(sysUserRoleMapper).insert(argThat((SysUserRole relation) ->
                Long.valueOf(2L).equals(relation.getUserId())
                        && Long.valueOf(3L).equals(relation.getRoleId())));
        verifyNoMoreInteractions(sysUserRoleMapper);
    }

    /**
     * 操作者只能授予自身持有的角色，防止自我提权。
     */
    @Test
    void shouldRejectAssigningRoleOperatorDoesNotOwn() {
        SysUser user = new SysUser();
        user.setId(2L);
        SysRole role = new SysRole();
        role.setId(3L);
        role.setRoleCode("MANAGER");
        when(sysUserMapper.selectById(2L)).thenReturn(user);
        when(sysRoleMapper.selectList(any())).thenReturn(List.of(role));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("manager", "n",
                        List.of(new SimpleGrantedAuthority("EDITOR"))));

        BizException exception = assertThrows(BizException.class,
                () -> sysUserService.assignRoles(2L, List.of(3L)));

        assertEquals("不能授予自身没有的角色", exception.getMessage());
        verifyNoInteractions(sysUserRoleMapper);
    }

    /**
     * ADMIN 操作者可以向用户分配自身未持有的普通角色。
     */
    @Test
    void shouldAllowAdminAssigningRoleOperatorDoesNotOwn() {
        SysUser user = new SysUser();
        user.setId(2L);
        SysRole role = new SysRole();
        role.setId(3L);
        role.setRoleCode("MANAGER");
        when(sysUserMapper.selectById(2L)).thenReturn(user);
        when(sysRoleMapper.selectList(any())).thenReturn(List.of(role));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("admin", "n",
                        List.of(new SimpleGrantedAuthority("ADMIN"))));

        sysUserService.assignRoles(2L, List.of(3L));

        verify(sysUserRoleMapper).delete(any());
        verify(sysUserRoleMapper).insert(argThat((SysUserRole relation) ->
                Long.valueOf(2L).equals(relation.getUserId())
                        && Long.valueOf(3L).equals(relation.getRoleId())));
        verifyNoMoreInteractions(sysUserRoleMapper);
    }

    /**
     * ADMIN 角色禁止通过用户管理接口重新分配。
     */
    @Test
    void shouldRejectAssigningAdminRole() {
        SysUser user = new SysUser();
        user.setId(2L);
        SysRole role = new SysRole();
        role.setId(3L);
        role.setRoleCode("ADMIN");
        when(sysUserMapper.selectById(2L)).thenReturn(user);
        when(sysRoleMapper.selectList(any())).thenReturn(List.of(role));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("admin", "n",
                        List.of(new SimpleGrantedAuthority("ADMIN"))));

        BizException exception = assertThrows(BizException.class,
                () -> sysUserService.assignRoles(2L, List.of(3L)));

        assertEquals("不可分配 ADMIN 角色", exception.getMessage());
        verifyNoInteractions(sysUserRoleMapper);
    }
}
