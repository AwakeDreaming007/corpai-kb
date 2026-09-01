package com.xufg.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xufg.handler.JsonbStringTypeHandler;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.LocalDateTime;

/**
 * 企业知识库问答历史。
 */
@Data
@TableName(value = "qa_history", autoResultMap = true)
public class QaHistory {

    /** 历史 ID，数据库自增。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 问答会话 ID。 */
    @TableField("session_id")
    private String sessionId;

    /** 知识库 ID。 */
    @TableField("kb_id")
    private Long kbId;

    /** 提问用户 ID。 */
    @TableField("user_id")
    private Long userId;

    /** 用户原始问题。 */
    @TableField("question")
    private String question;

    /** 模型最终回答。 */
    @TableField("answer")
    private String answer;

    /**
     * 引用来源 JSONB。写入前手工序列化为 JSON 字符串，读取时再解析成数组；
     * 专用 TypeHandler 负责包装为 PGobject，保证 PostgreSQL 目标类型匹配。
     */
    @TableField(value = "sources", typeHandler = JsonbStringTypeHandler.class, jdbcType = JdbcType.OTHER)
    private String sources;

    /** 回答使用的模型标识。 */
    @TableField("model")
    private String model;

    /** 回答耗时毫秒数。 */
    @TableField("latency_ms")
    private Long latencyMs;

    /** 创建时间。 */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
