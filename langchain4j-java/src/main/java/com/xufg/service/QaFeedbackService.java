package com.xufg.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xufg.common.BizException;
import com.xufg.common.UserContext;
import com.xufg.entity.QaFeedback;
import com.xufg.entity.QaHistory;
import com.xufg.mapper.QaFeedbackMapper;
import com.xufg.mapper.QaHistoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 企业知识库问答反馈服务。
 */
@Service
@RequiredArgsConstructor
public class QaFeedbackService {

    /** 问答反馈 Mapper。 */
    private final QaFeedbackMapper qaFeedbackMapper;

    /** 问答历史 Mapper。 */
    private final QaHistoryMapper qaHistoryMapper;

    /**
     * 新增或更新当前用户对指定历史的反馈；rating 为 0 时取消并删除反馈。
     *
     * @param historyId 问答历史 ID
     * @param rating 评分：1 点赞 / -1 点踩 / 0 取消
     * @param reason 反馈原因
     */
    @Transactional
    public void upsert(Long historyId, Integer rating, String reason) {
        QaHistory history = requireOwnHistory(historyId);
        Long userId = UserContext.getUserId();

        if (rating == 0) {
            qaFeedbackMapper.delete(Wrappers.<QaFeedback>lambdaQuery()
                    .eq(QaFeedback::getHistoryId, history.getId())
                    .eq(QaFeedback::getUserId, userId));
            return;
        }

        qaFeedbackMapper.upsert(history.getId(), userId, rating, reason);
    }

    /**
     * 查询当前用户对指定历史的反馈，无反馈时返回 null。
     *
     * @param historyId 问答历史 ID
     * @return 当前用户反馈
     */
    public QaFeedback getByHistoryId(Long historyId) {
        QaHistory history = requireOwnHistory(historyId);
        return qaFeedbackMapper.selectOne(Wrappers.<QaFeedback>lambdaQuery()
                .eq(QaFeedback::getHistoryId, history.getId())
                .eq(QaFeedback::getUserId, UserContext.getUserId()));
    }

    /**
     * 查询本人历史，不存在返回 404，非本人返回 403。
     */
    private QaHistory requireOwnHistory(Long historyId) {
        QaHistory history = qaHistoryMapper.selectById(historyId);
        if (history == null) {
            throw new BizException(404, "问答历史不存在");
        }
        if (!history.getUserId().equals(UserContext.getUserId())) {
            throw new BizException(403, "无权访问");
        }
        return history;
    }
}
