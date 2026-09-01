package com.xufg.dto;

import lombok.Data;

import java.util.List;

/**
 * 角色管理列表数据。
 */
@Data
public class RoleListResponse {

    /** 角色 ID。 */
    private Long id;

    /** 角色编码。 */
    private String roleCode;

    /** 角色名称。 */
    private String roleName;

    /** 角色描述。 */
    private String description;

    /** 是否内置角色。 */
    private Boolean builtIn;

    /** 角色权限编码列表。 */
    private List<String> permissions;
}
