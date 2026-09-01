package com.xufg.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求参数。
 */
@Data
public class RegisterRequest {

    /** 登录用户名。 */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 登录密码。 */
    @NotBlank(message = "密码不能为空")
    private String password;

    /** 用户昵称。 */
    @Size(max = 50, message = "昵称长度不能超过 50 个字符")
    private String nickname;
}
