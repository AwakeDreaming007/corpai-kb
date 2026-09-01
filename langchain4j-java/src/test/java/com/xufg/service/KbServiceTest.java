package com.xufg.service;

import com.xufg.common.BizException;
import com.xufg.common.UserContext;
import com.xufg.entity.KbBase;
import com.xufg.entity.KbMember;
import com.xufg.enums.MemberRole;
import com.xufg.mapper.KbBaseMapper;
import com.xufg.mapper.KbDocumentMapper;
import com.xufg.mapper.KbMemberMapper;
import com.xufg.mapper.SysUserMapper;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 知识库服务单元测试。
 */
@ExtendWith(MockitoExtension.class)
class KbServiceTest {

    /** 知识库 Mapper。 */
    @Mock
    private KbBaseMapper kbBaseMapper;

    /** 知识库成员 Mapper。 */
    @Mock
    private KbMemberMapper kbMemberMapper;

    /** 知识库文档 Mapper。 */
    @Mock
    private KbDocumentMapper kbDocumentMapper;

    /** 用户 Mapper。 */
    @Mock
    private SysUserMapper sysUserMapper;

    /** 库内权限校验服务。 */
    @Mock
    private KbPermissionService kbPermissionService;

    /** 向量存储。 */
    @Mock
    private EmbeddingStore<TextSegment> embeddingStore;

    /** 事务模板。 */
    @Mock
    private TransactionTemplate transactionTemplate;

    /** 被测知识库服务。 */
    @InjectMocks
    private KbService kbService;

    /**
     * 清理当前用户上下文。
     */
    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    /**
     * 同一用户重复创建同名知识库时拒绝。
     */
    @Test
    void shouldRejectDuplicateKbName() {
        UserContext.set(1L, "tester");
        when(kbBaseMapper.selectCount(any())).thenReturn(1L);

        BizException exception = assertThrows(BizException.class,
                () -> kbService.create("测试库", "描述"));

        assertEquals("知识库名称已存在", exception.getMessage());
        verifyNoInteractions(kbMemberMapper, sysUserMapper, embeddingStore, transactionTemplate);
    }

    /**
     * 创建知识库后自动写入当前用户的 OWNER 成员记录。
     */
    @Test
    void shouldCreateOwnerMemberWhenKbCreated() {
        UserContext.set(1L, "tester");
        when(kbBaseMapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> {
            KbBase kbBase = invocation.getArgument(0);
            kbBase.setId(10L);
            return 1;
        }).when(kbBaseMapper).insert(any(KbBase.class));

        kbService.create("测试库", "描述");

        ArgumentCaptor<KbMember> memberCaptor = ArgumentCaptor.forClass(KbMember.class);
        verify(kbMemberMapper).insert(memberCaptor.capture());
        assertEquals(10L, memberCaptor.getValue().getKbId());
        assertEquals(1L, memberCaptor.getValue().getUserId());
        assertEquals(MemberRole.OWNER.name(), memberCaptor.getValue().getMemberRole());
    }

    /**
     * 非库主且没有 kb:manage 权限时拒绝更新。
     * <p>
     * assertManageable 已改为 findRoleOrNull 分支判断（避免事务内捕获异常导致
     * rollback-only），因此这里 stub findRoleOrNull 返回 EDITOR（成员但非库主）。
     */
    @Test
    void shouldRejectUpdateWithoutOwnerOrManageAuthority() {
        UserContext.set(1L, "tester");
        KbBase kbBase = new KbBase();
        kbBase.setId(10L);
        kbBase.setOwnerUserId(2L);
        when(kbBaseMapper.selectById(10L)).thenReturn(kbBase);
        when(kbPermissionService.findRoleOrNull(10L, 1L)).thenReturn(MemberRole.EDITOR);

        BizException exception = assertThrows(BizException.class,
                () -> kbService.update(10L, "新名称", "新描述"));

        assertEquals("权限不足", exception.getMessage());
        assertEquals(403, exception.getCode());
        verify(kbBaseMapper).selectById(10L);
        verify(kbPermissionService).findRoleOrNull(10L, 1L);
        verifyNoInteractions(embeddingStore, transactionTemplate);
    }
}
