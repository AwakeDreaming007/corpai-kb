package com.xufg.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 企业知识库问答反馈。
 */
@Data
@TableName("qa_feedback")
public class QaFeedback {

    /** 反馈 ID，数据库自增。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 问答历史 ID。 */
    @TableField("history_id")
    private Long historyId;

    /** 反馈用户 ID。 */
    @TableField("user_id")
    private Long userId;

    /** 评分：1 点赞 / -1 点踩。 */
    @TableField("rating")
    private Integer rating;

    /** 反馈原因。 */
    @TableField("reason")
    private String reason;

    /** 创建时间。 */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
