package com.xufg.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 更新知识库成员角色请求。
 */
@Data
public class KbMemberRoleRequest {

    /** 目标成员的新库内角色。 */
    @NotNull(message = "成员角色不能为空")
    private String memberRole;
}
