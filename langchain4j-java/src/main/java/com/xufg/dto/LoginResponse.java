package com.xufg.dto;

import lombok.Data;

import java.util.List;

/**
 * 登录响应数据。
 */
@Data
public class LoginResponse {

    /** JWT 令牌。 */
    private String token;

    /** 用户 ID。 */
    private Long userId;

    /** 用户名。 */
    private String username;

    /** 用户昵称。 */
    private String nickname;

    /** 用户角色编码。 */
    private List<String> roles;

    /** 用户权限编码。 */
    private List<String> permissions;
}
