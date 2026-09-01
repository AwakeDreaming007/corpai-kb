package com.xufg.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 问答反馈请求。
 */
@Data
public class FeedbackRequest {

    /** 问答历史 ID。 */
    @NotNull(message = "historyId 不能为空")
    private Long historyId;

    /** 评分：1 点赞 / -1 点踩 / 0 取消。 */
    @NotNull(message = "rating 不能为空")
    @Min(value = -1, message = "rating 只能为 -1、0 或 1")
    @Max(value = 1, message = "rating 只能为 -1、0 或 1")
    private Integer rating;

    /** 反馈原因，最长 500 字符。 */
    @Size(max = 500, message = "反馈原因最长 500 字符")
    private String reason;
}
