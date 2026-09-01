package com.xufg.service;

import com.xufg.common.BizException;
import com.xufg.common.UserContext;
import com.xufg.entity.KbBase;
import com.xufg.entity.KbMember;
import com.xufg.entity.SysUser;
import com.xufg.enums.MemberRole;
import com.xufg.mapper.KbBaseMapper;
import com.xufg.mapper.KbMemberMapper;
import com.xufg.mapper.SysUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import org.junit.jupiter.api.BeforeAll;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 知识库成员服务单元测试。
 */
@ExtendWith(MockitoExtension.class)
class KbMemberServiceTest {

    /**
     * 初始化 MyBatis-Plus 实体元数据，让单元测试能执行 Lambda 条件构造器。
     */
    @BeforeAll
    static void initTableMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), KbMember.class);
    }

    /** 知识库 Mapper。 */
    @Mock
    private KbBaseMapper kbBaseMapper;

    /** 知识库成员 Mapper。 */
    @Mock
    private KbMemberMapper kbMemberMapper;

    /** 用户 Mapper。 */
    @Mock
    private SysUserMapper sysUserMapper;

    /** 库内权限校验服务。 */
    @Mock
    private KbPermissionService kbPermissionService;

    /** 被测知识库成员服务。 */
    @InjectMocks
    private KbMemberService kbMemberService;

    /**
     * 清理当前用户上下文。
     */
    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    /**
     * 构造存在的知识库并授权当前库主。
     */
    private void prepareOwnerAccess(Long kbId) {
        KbBase kbBase = new KbBase();
        kbBase.setId(kbId);
        when(kbBaseMapper.selectById(kbId)).thenReturn(kbBase);
        when(kbPermissionService.assertMember(kbId, 1L, MemberRole.OWNER)).thenReturn(MemberRole.OWNER);
    }

    /**
     * 添加用户时如果用户不存在返回 404。
     */
    @Test
    void shouldRejectAddingMissingUser() {
        UserContext.set(1L, "owner");
        prepareOwnerAccess(10L);
        when(sysUserMapper.selectList(any())).thenReturn(List.of());

        BizException exception = assertThrows(BizException.class,
                () -> kbMemberService.addMember(10L, "editor", "EDITOR"));

        assertEquals(404, exception.getCode());
        assertEquals("用户不存在", exception.getMessage());
        verifyNoInteractions(kbMemberMapper);
    }

    /**
     * 重复添加已有成员时拒绝。
     */
    @Test
    void shouldRejectAddingExistingMember() {
        UserContext.set(1L, "owner");
        prepareOwnerAccess(10L);
        SysUser user = new SysUser();
        user.setId(2L);
        user.setUsername("editor");
        when(sysUserMapper.selectList(any())).thenReturn(List.of(user));
        when(kbMemberMapper.selectCount(any())).thenReturn(1L);

        BizException exception = assertThrows(BizException.class,
                () -> kbMemberService.addMember(10L, "editor", "EDITOR"));

        assertEquals("该用户已是成员", exception.getMessage());
        verify(kbMemberMapper).selectCount(any());
    }

    /**
     * 唯一库主不能被移除。
     */
    @Test
    void shouldRejectRemovingOnlyOwner() {
        UserContext.set(1L, "owner");
        prepareOwnerAccess(10L);
        KbMember target = new KbMember();
        target.setId(20L);
        target.setKbId(10L);
        target.setUserId(2L);
        target.setMemberRole(MemberRole.OWNER.name());
        when(kbMemberMapper.selectOne(any())).thenReturn(target);
        when(kbMemberMapper.selectCount(any())).thenReturn(1L);

        BizException exception = assertThrows(BizException.class,
                () -> kbMemberService.removeMember(10L, 2L));

        assertEquals("请先转让库主", exception.getMessage());
        verify(kbMemberMapper).selectCount(any());
    }

    /**
     * 转让库主后，原库主自动降为编辑者。
     */
    @Test
    void shouldDemoteOldOwnerWhenTransferringOwnership() {
        UserContext.set(1L, "owner");
        prepareOwnerAccess(10L);
        KbMember target = new KbMember();
        target.setId(20L);
        target.setKbId(10L);
        target.setUserId(2L);
        target.setMemberRole(MemberRole.EDITOR.name());
        KbMember oldOwner = new KbMember();
        oldOwner.setId(10L);
        oldOwner.setKbId(10L);
        oldOwner.setUserId(1L);
        oldOwner.setMemberRole(MemberRole.OWNER.name());
        when(kbMemberMapper.selectOne(any())).thenReturn(target, oldOwner);

        when(kbMemberMapper.update(isNull(), any())).thenReturn(1);
        when(kbMemberMapper.updateById(any(KbMember.class))).thenReturn(1);
        when(kbBaseMapper.updateById(any(KbBase.class))).thenReturn(1);

        kbMemberService.updateMemberRole(10L, 2L, "OWNER");

        ArgumentCaptor<KbMember> memberCaptor = ArgumentCaptor.forClass(KbMember.class);
        ArgumentCaptor<KbBase> kbCaptor = ArgumentCaptor.forClass(KbBase.class);
        verify(kbMemberMapper).update(isNull(), any());
        verify(kbMemberMapper).updateById(memberCaptor.capture());
        assertEquals(MemberRole.OWNER.name(), memberCaptor.getValue().getMemberRole());
        verify(kbBaseMapper).updateById(kbCaptor.capture());
        assertEquals(2L, kbCaptor.getValue().getOwnerUserId());
    }
}
