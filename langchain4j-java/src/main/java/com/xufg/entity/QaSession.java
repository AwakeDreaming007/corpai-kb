package com.xufg.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 企业知识库问答会话。
 */
@Data
@TableName("qa_session")
public class QaSession {

    /** 会话 ID，同时作为 LangChain4j 的 memoryId。 */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /** 知识库 ID。 */
    @TableField("kb_id")
    private Long kbId;

    /** 提问用户 ID。 */
    @TableField("user_id")
    private Long userId;

    /** 会话标题，首次提问时截取。 */
    @TableField("title")
    private String title;

    /** 创建时间。 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 最近活跃时间。 */
    @TableField("last_active_at")
    private LocalDateTime lastActiveAt;
}
