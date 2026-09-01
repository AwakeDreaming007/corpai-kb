package com.xufg.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 用户与角色关联表。
 */
@Data
@TableName("sys_user_role")
public class SysUserRole {

    /** 用户 ID。 */
    @TableField("user_id")
    private Long userId;

    /** 角色 ID。 */
    @TableField("role_id")
    private Long roleId;
}
