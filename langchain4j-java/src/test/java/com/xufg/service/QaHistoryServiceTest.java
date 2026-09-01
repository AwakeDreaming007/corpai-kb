package com.xufg.service;

import com.xufg.common.BizException;
import com.xufg.common.UserContext;
import com.xufg.entity.QaHistory;
import com.xufg.mapper.QaHistoryMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 问答历史服务单元测试。
 */
@ExtendWith(MockitoExtension.class)
class QaHistoryServiceTest {

    /** 问答历史 Mapper。 */
    @Mock
    private QaHistoryMapper qaHistoryMapper;

    /** 被测问答历史服务。 */
    @InjectMocks
    private QaHistoryService qaHistoryService;

    /**
     * 清理当前用户上下文。
     */
    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    /**
     * 他人历史详情必须返回 403。
     */
    @Test
    void shouldRejectOtherUsersHistoryDetail() {
        UserContext.set(1L, "tester");
        when(qaHistoryMapper.selectById(9L)).thenReturn(buildHistory(2L));

        BizException exception = assertThrows(BizException.class,
                () -> qaHistoryService.detail(9L));

        assertEquals(403, exception.getCode());
        assertEquals("无权访问", exception.getMessage());
    }

    /**
     * 不存在的历史详情必须返回 404。
     */
    @Test
    void shouldRejectMissingHistoryDetail() {
        UserContext.set(1L, "tester");
        when(qaHistoryMapper.selectById(9L)).thenReturn(null);

        BizException exception = assertThrows(BizException.class,
                () -> qaHistoryService.detail(9L));

        assertEquals(404, exception.getCode());
        assertEquals("问答历史不存在", exception.getMessage());
    }

    /**
     * 历史落库失败只记录日志，不允许影响问答主流程。
     */
    @Test
    void shouldSwallowSaveAsyncException() {
        doThrow(new RuntimeException("数据库不可用")).when(qaHistoryMapper).insert(any(QaHistory.class));

        assertDoesNotThrow(() -> qaHistoryService.saveAsync("session-1", 10L, 1L,
                "公司差旅标准是什么", "经济舱可报销。", List.of(Map.of("docId", 101)),
                "deepseek-stream", 120L));
        verify(qaHistoryMapper).insert(any(QaHistory.class));
    }

    /**
     * 构建测试问答历史。
     */
    private QaHistory buildHistory(Long userId) {
        QaHistory history = new QaHistory();
        history.setId(9L);
        history.setSessionId("session-1");
        history.setKbId(10L);
        history.setUserId(userId);
        history.setQuestion("公司差旅标准是什么");
        return history;
    }
}
