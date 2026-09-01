package com.xufg.service;

import com.xufg.common.BizException;
import com.xufg.dto.RoleCreateRequest;
import com.xufg.dto.RoleUpdateRequest;
import com.xufg.entity.SysRole;
import com.xufg.entity.SysRolePermission;
import com.xufg.mapper.SysPermissionMapper;
import com.xufg.mapper.SysRoleMapper;
import com.xufg.mapper.SysRolePermissionMapper;
import com.xufg.mapper.SysUserRoleMapper;
import com.xufg.entity.SysPermission;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 系统角色管理服务单元测试。
 */
@ExtendWith(MockitoExtension.class)
class SysRoleServiceTest {

    /** 角色 Mapper。 */
    @Mock
    private SysRoleMapper sysRoleMapper;

    /** 权限 Mapper。 */
    @Mock
    private SysPermissionMapper sysPermissionMapper;

    /** 用户角色关联 Mapper。 */
    @Mock
    private SysUserRoleMapper sysUserRoleMapper;

    /** 角色权限关联 Mapper。 */
    @Mock
    private SysRolePermissionMapper sysRolePermissionMapper;

    /** 被测角色服务。 */
    @InjectMocks
    private SysRoleService sysRoleService;

    /**
     * 清理安全上下文，避免用例之间相互污染。
     */
    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /**
     * 校验内置角色拒绝删除。
     */
    @Test
    void shouldRejectDeletingBuiltInRole() {
        SysRole role = new SysRole();
        role.setId(1L);
        role.setBuiltIn(true);
        when(sysRoleMapper.selectById(1L)).thenReturn(role);

        BizException exception = assertThrows(BizException.class, () -> sysRoleService.deleteRole(1L));

        assertEquals("内置角色不可删除", exception.getMessage());
        verify(sysRolePermissionMapper, never()).delete(any());
        verifyNoInteractions(sysRolePermissionMapper);
    }

    /**
     * 校验已绑定用户的自定义角色拒绝删除。
     */
    @Test
    void shouldRejectDeletingRoleWithBoundUsers() {
        SysRole role = new SysRole();
        role.setId(1L);
        role.setBuiltIn(false);
        when(sysRoleMapper.selectById(1L)).thenReturn(role);
        when(sysUserRoleMapper.selectCount(any())).thenReturn(2L);

        BizException exception = assertThrows(BizException.class, () -> sysRoleService.deleteRole(1L));

        assertEquals("请先解绑用户", exception.getMessage());
        verifyNoInteractions(sysRolePermissionMapper);
    }

    /**
     * 校验解绑后的自定义角色先清权限再删除。
     */
    @Test
    void shouldDeleteUnboundCustomRoleWithPermissions() {
        SysRole role = new SysRole();
        role.setId(1L);
        role.setBuiltIn(false);
        when(sysRoleMapper.selectById(1L)).thenReturn(role);
        when(sysUserRoleMapper.selectCount(any())).thenReturn(0L);

        sysRoleService.deleteRole(1L);

        InOrder inOrder = inOrder(sysRolePermissionMapper, sysRoleMapper);
        inOrder.verify(sysRolePermissionMapper).delete(any());
        inOrder.verify(sysRoleMapper).deleteById(1L);
    }

    /**
     * 内置角色的权限绑定由初始化 SQL 固定，接口必须拒绝修改。
     */
    @Test
    void shouldRejectAssigningPermissionsToBuiltInRole() {
        SysRole role = new SysRole();
        role.setId(1L);
        role.setBuiltIn(true);
        when(sysRoleMapper.selectById(1L)).thenReturn(role);

        BizException exception = assertThrows(BizException.class,
                () -> sysRoleService.assignPermissions(1L, List.of(10L)));

        assertEquals("内置角色权限不可修改", exception.getMessage());
        verifyNoInteractions(sysPermissionMapper, sysRolePermissionMapper);
    }

    /**
     * 操作者只能授予自己已持有的权限，防止自定义角色分配形成垂直越权。
     */
    @Test
    void shouldRejectGrantingPermissionOperatorDoesNotOwn() {
        SysRole role = new SysRole();
        role.setId(1L);
        role.setBuiltIn(false);
        when(sysRoleMapper.selectById(1L)).thenReturn(role);
        when(sysPermissionMapper.selectList(any()))
                .thenReturn(List.of(permission(20L, "kb:delete")));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        "manager", "n", List.of(new SimpleGrantedAuthority("kb:create"))));

        BizException exception = assertThrows(BizException.class,
                () -> sysRoleService.assignPermissions(1L, List.of(20L)));

        assertEquals("不能授予自身没有的权限", exception.getMessage());
        verifyNoInteractions(sysRolePermissionMapper);
    }

    /**
     * 并发创建角色撞唯一索引时必须返回业务错误。
     */
    @Test
    void shouldMapCreateRoleDuplicateKeyToBadRequest() {
        RoleCreateRequest request = new RoleCreateRequest();
        request.setRoleCode("MANAGER");
        request.setRoleName("经理");
        when(sysRoleMapper.selectCount(any())).thenReturn(0L);
        when(sysRoleMapper.insert(any(SysRole.class))).thenThrow(new DuplicateKeyException("duplicate"));

        BizException exception = assertThrows(BizException.class, () -> sysRoleService.createRole(request));

        assertEquals(400, exception.getCode());
    }

    /**
     * 并发更新角色编码撞唯一索引时必须返回业务错误。
     */
    @Test
    void shouldMapUpdateRoleDuplicateKeyToBadRequest() {
        RoleUpdateRequest request = new RoleUpdateRequest();
        request.setRoleCode("MANAGER");
        request.setRoleName("经理");
        SysRole role = new SysRole();
        role.setId(1L);
        role.setRoleCode("OLD");
        role.setBuiltIn(false);
        when(sysRoleMapper.selectById(1L)).thenReturn(role);
        when(sysRoleMapper.selectCount(any())).thenReturn(0L);
        when(sysRoleMapper.updateById(any(SysRole.class))).thenThrow(new DuplicateKeyException("duplicate"));

        BizException exception = assertThrows(BizException.class,
                () -> sysRoleService.updateRole(1L, request));

        assertEquals(400, exception.getCode());
    }

    /**
     * 并发分配权限撞唯一索引时必须返回业务错误。
     */
    @Test
    void shouldMapAssignPermissionsDuplicateKeyToBadRequest() {
        SysRole role = new SysRole();
        role.setId(1L);
        role.setBuiltIn(false);
        when(sysRoleMapper.selectById(1L)).thenReturn(role);
        when(sysPermissionMapper.selectList(any())).thenReturn(List.of(permission(20L, "kb:create")));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("manager", "n",
                        List.of(new SimpleGrantedAuthority("kb:create"))));
        when(sysRolePermissionMapper.insert(any(SysRolePermission.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));

        BizException exception = assertThrows(BizException.class,
                () -> sysRoleService.assignPermissions(1L, List.of(20L)));

        assertEquals(400, exception.getCode());
    }

    /**
     * 构造权限实体。
     */
    private SysPermission permission(Long id, String permCode) {
        SysPermission permission = new SysPermission();
        permission.setId(id);
        permission.setPermCode(permCode);
        return permission;
    }
}
