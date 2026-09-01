package com.xufg.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 角色与权限关联表。
 */
@Data
@TableName("sys_role_permission")
public class SysRolePermission {

    /** 角色 ID。 */
    @TableField("role_id")
    private Long roleId;

    /** 权限 ID。 */
    @TableField("perm_id")
    private Long permId;
}
