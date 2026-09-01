package com.xufg.service;

import com.xufg.common.BizException;
import com.xufg.common.UserContext;
import com.xufg.entity.QaSession;
import com.xufg.enums.MemberRole;
import com.xufg.mapper.QaSessionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 问答会话服务单元测试。
 */
@ExtendWith(MockitoExtension.class)
class QaSessionServiceTest {

    /** 问答会话 Mapper。 */
    @Mock
    private QaSessionMapper qaSessionMapper;

    /** 库内权限校验服务。 */
    @Mock
    private KbPermissionService kbPermissionService;

    /** 被测问答会话服务。 */
    @InjectMocks
    private QaSessionService qaSessionService;

    /**
     * 清理当前用户上下文。
     */
    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    /**
     * 非成员创建会话时传播权限异常。
     */
    @Test
    void shouldRejectCreateWhenNotMember() {
        UserContext.set(1L, "tester");
        when(kbPermissionService.assertMember(10L, 1L, MemberRole.VIEWER))
                .thenThrow(new BizException(403, "无权访问该知识库"));

        BizException exception = assertThrows(BizException.class,
                () -> qaSessionService.create(10L));

        assertEquals("无权访问该知识库", exception.getMessage());
        verify(kbPermissionService).assertMember(10L, 1L, MemberRole.VIEWER);
    }

    /**
     * 创建会话时生成 UUID、绑定当前用户并返回会话 ID。
     */
    @Test
    void shouldCreateSessionWithUuidAndCurrentUser() {
        UserContext.set(1L, "tester");

        String sessionId = qaSessionService.create(10L);

        ArgumentCaptor<QaSession> captor = ArgumentCaptor.forClass(QaSession.class);
        verify(qaSessionMapper).insert(captor.capture());
        assertEquals(sessionId, captor.getValue().getId());
        assertEquals(36, sessionId.length());
        assertEquals(10L, captor.getValue().getKbId());
        assertEquals(1L, captor.getValue().getUserId());
        assertNotNull(captor.getValue().getLastActiveAt());
    }

    /**
     * 他人会话返回 403，而不是暴露会话是否存在。
     */
    @Test
    void shouldRejectOtherUsersSession() {
        UserContext.set(1L, "tester");
        QaSession session = buildSession(10L, 2L, null);
        when(qaSessionMapper.selectById("session-1")).thenReturn(session);

        BizException exception = assertThrows(BizException.class,
                () -> qaSessionService.requireOwnSession("session-1", 10L));

        assertEquals(403, exception.getCode());
        assertEquals("无权访问该会话", exception.getMessage());
    }

    /**
     * 会话归属正确但知识库不匹配时返回 400。
     */
    @Test
    void shouldRejectSessionFromAnotherKb() {
        UserContext.set(1L, "tester");
        QaSession session = buildSession(20L, 1L, null);
        when(qaSessionMapper.selectById("session-1")).thenReturn(session);

        BizException exception = assertThrows(BizException.class,
                () -> qaSessionService.requireOwnSession("session-1", 10L));

        assertEquals(400, exception.getCode());
        assertEquals("会话与知识库不匹配", exception.getMessage());
    }

    /**
     * 首次提问时截取前 30 个字符作为标题。
     */
    @Test
    void shouldTrimFirstQuestionAsTitle() {
        String question = "这是一条用于测试标题截取逻辑的问题，需要超过三十个字符，请正确截取";
        QaSession session = buildSession(10L, 1L, null);
        when(qaSessionMapper.selectById("session-1")).thenReturn(session);

        qaSessionService.updateTitleIfAbsent("session-1", question);

        ArgumentCaptor<QaSession> captor = ArgumentCaptor.forClass(QaSession.class);
        verify(qaSessionMapper).updateById(captor.capture());
        assertEquals(question.substring(0, 30), captor.getValue().getTitle());
        assertNotNull(captor.getValue().getLastActiveAt());
    }

    /**
     * 构建测试会话。
     */
    private QaSession buildSession(Long kbId, Long userId, String title) {
        QaSession session = new QaSession();
        session.setId("session-1");
        session.setKbId(kbId);
        session.setUserId(userId);
        session.setTitle(title);
        session.setCreatedAt(LocalDateTime.now());
        return session;
    }
}
