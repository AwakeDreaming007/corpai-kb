package com.xufg.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 自定义角色创建请求。
 */
@Data
public class RoleCreateRequest {

    /** 角色编码。 */
    @NotBlank(message = "角色编码不能为空")
    @Size(max = 50, message = "角色编码不能超过 50 个字符")
    private String roleCode;

    /** 角色名称。 */
    @NotBlank(message = "角色名称不能为空")
    @Size(max = 50, message = "角色名称不能超过 50 个字符")
    private String roleName;

    /** 角色描述。 */
    @Size(max = 200, message = "角色描述不能超过 200 个字符")
    private String description;
}
