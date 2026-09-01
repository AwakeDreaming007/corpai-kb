package com.xufg.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 添加知识库成员请求。
 */
@Data
public class KbMemberAddRequest {

    /** 被添加用户的登录名。 */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 被添加用户的库内角色。 */
    @NotNull(message = "成员角色不能为空")
    private String memberRole;
}
