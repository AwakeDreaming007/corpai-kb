package com.xufg.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 流式知识库问答请求。
 */
@Data
public class ChatRequest {

    /** 问答会话 ID。 */
    @NotBlank(message = "sessionId 不能为空")
    private String sessionId;

    /** 用户问题。 */
    @NotBlank(message = "问题不能为空")
    @Size(max = 2000, message = "问题最长 2000 字符")
    private String question;
}
