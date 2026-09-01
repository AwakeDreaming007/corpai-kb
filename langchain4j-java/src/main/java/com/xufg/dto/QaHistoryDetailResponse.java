package com.xufg.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 问答历史详情响应。
 */
@Data
public class QaHistoryDetailResponse {

    /** 历史 ID。 */
    private Long id;

    /** 问答会话 ID。 */
    private String sessionId;

    /** 知识库 ID。 */
    private Long kbId;

    /** 提问用户 ID。 */
    private Long userId;

    /** 用户原始问题。 */
    private String question;

    /** 模型最终回答。 */
    private String answer;

    /** 引用来源数组。 */
    private List<Map<String, Object>> sources;

    /** 回答使用的模型标识。 */
    private String model;

    /** 回答耗时毫秒数。 */
    private Long latencyMs;

    /** 创建时间。 */
    private LocalDateTime createdAt;
}
