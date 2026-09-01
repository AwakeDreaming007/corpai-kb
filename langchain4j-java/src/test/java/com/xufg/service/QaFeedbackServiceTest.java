package com.xufg.service;

import com.xufg.common.BizException;
import com.xufg.common.UserContext;
import com.xufg.entity.QaFeedback;
import com.xufg.entity.QaHistory;
import com.xufg.mapper.QaFeedbackMapper;
import com.xufg.mapper.QaHistoryMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 问答反馈服务单元测试。
 */
@ExtendWith(MockitoExtension.class)
class QaFeedbackServiceTest {

    /** 问答反馈 Mapper。 */
    @Mock
    private QaFeedbackMapper qaFeedbackMapper;

    /** 问答历史 Mapper。 */
    @Mock
    private QaHistoryMapper qaHistoryMapper;

    /** 被测问答反馈服务。 */
    @InjectMocks
    private QaFeedbackService qaFeedbackService;

    /**
     * 清理当前用户上下文。
     */
    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    /**
     * 首次或重复反馈统一走数据库原子 upsert，避免 PG 并发冲突导致事务中毒。
     */
    @Test
    void shouldUpsertFeedbackAtomically() {
        UserContext.set(1L, "tester");
        prepareOwnHistory();

        qaFeedbackService.upsert(9L, 1, "很有帮助");

        verify(qaFeedbackMapper).upsert(9L, 1L, 1, "很有帮助");
        verify(qaFeedbackMapper, never()).insert(any(QaFeedback.class));
        verify(qaFeedbackMapper, never()).updateById(any(QaFeedback.class));
    }

    /**
     * 负分反馈同样通过数据库原子 upsert 落库。
     */
    @Test
    void shouldUpsertNegativeFeedback() {
        UserContext.set(1L, "tester");
        prepareOwnHistory();

        qaFeedbackService.upsert(9L, -1, "答案不准确");

        verify(qaFeedbackMapper).upsert(9L, 1L, -1, "答案不准确");
        verify(qaFeedbackMapper, never()).insert(any(QaFeedback.class));
    }

    /**
     * rating 为 0 表示取消反馈，直接删除当前用户记录。
     */
    @Test
    void shouldDeleteFeedbackWhenRatingIsZero() {
        UserContext.set(1L, "tester");
        prepareOwnHistory();

        qaFeedbackService.upsert(9L, 0, null);

        verify(qaFeedbackMapper).delete(any());
        verify(qaFeedbackMapper, never()).insert(any(QaFeedback.class));
        verify(qaFeedbackMapper, never()).updateById(any(QaFeedback.class));
    }

    /**
     * 非本人历史反馈必须返回 403。
     */
    @Test
    void shouldRejectFeedbackForOtherUsersHistory() {
        UserContext.set(1L, "tester");
        QaHistory history = new QaHistory();
        history.setId(9L);
        history.setUserId(2L);
        when(qaHistoryMapper.selectById(9L)).thenReturn(history);

        BizException exception = assertThrows(BizException.class,
                () -> qaFeedbackService.upsert(9L, 1, "无权操作"));

        assertEquals(403, exception.getCode());
        assertEquals("无权访问", exception.getMessage());
    }

    /**
     * 准备当前用户自有问答历史。
     */
    private void prepareOwnHistory() {
        QaHistory history = new QaHistory();
        history.setId(9L);
        history.setUserId(1L);
        when(qaHistoryMapper.selectById(9L)).thenReturn(history);
    }
}
