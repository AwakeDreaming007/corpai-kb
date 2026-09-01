package com.xufg.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 用户角色分配请求。
 */
@Data
public class UserRoleAssignRequest {

    /** 目标角色 ID 集合。 */
    @NotNull(message = "角色 ID 集合不能为空")
    private List<Long> roleIds;
}
