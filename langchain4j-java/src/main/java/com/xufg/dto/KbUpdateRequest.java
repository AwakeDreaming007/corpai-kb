package com.xufg.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新知识库请求。
 */
@Data
public class KbUpdateRequest {

    /** 知识库名称。 */
    @NotBlank(message = "知识库名称不能为空")
    @Size(max = 100, message = "知识库名称最长 100 个字符")
    private String name;

    /** 知识库描述。 */
    @Size(max = 500, message = "知识库描述最长 500 个字符")
    private String description;
}
