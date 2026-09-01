package com.xufg.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xufg.entity.QaFeedback;

/**
 * 问答反馈 Mapper。
 */
public interface QaFeedbackMapper extends BaseMapper<QaFeedback> {

    /**
     * 原子新增或更新反馈，避免并发插入冲突后 PostgreSQL 事务进入 aborted 状态。
     */
    @Insert("""
            INSERT INTO qa_feedback(history_id, user_id, rating, reason, created_at)
            VALUES (#{historyId}, #{userId}, #{rating}, #{reason}, CURRENT_TIMESTAMP)
            ON CONFLICT (history_id, user_id)
            DO UPDATE SET rating = EXCLUDED.rating, reason = EXCLUDED.reason
            """)
    int upsert(@Param("historyId") Long historyId,
               @Param("userId") Long userId,
               @Param("rating") Integer rating,
               @Param("reason") String reason);
}
