package com.xufg.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 角色权限分配请求。
 */
@Data
public class RolePermissionAssignRequest {

    /** 目标权限 ID 集合。 */
    @NotNull(message = "权限 ID 集合不能为空")
    private List<Long> permIds;
}
