package com.xufg.dto;

import lombok.Data;

import java.util.List;

/**
 * 用户管理列表数据。
 */
@Data
public class UserListResponse {

    /** 用户 ID。 */
    private Long id;

    /** 登录用户名。 */
    private String username;

    /** 用户昵称。 */
    private String nickname;

    /** 用户状态：1 启用， 0 禁用。 */
    private Integer status;

    /** 用户角色编码列表。 */
    private List<String> roles;
}
