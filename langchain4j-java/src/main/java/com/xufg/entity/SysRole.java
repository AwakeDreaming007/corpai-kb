package com.xufg.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统角色。
 */
@Data
@TableName("sys_role")
public class SysRole {

    /** 角色 ID。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 角色编码， 例如 USER、ADMIN。 */
    @TableField("role_code")
    private String roleCode;

    /** 角色名称。 */
    @TableField("role_name")
    private String roleName;

    /** 角色描述。 */
    @TableField("description")
    private String description;

    /** 是否内置角色。 */
    @TableField("built_in")
    private Boolean builtIn;

    /** 创建时间。 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间。 */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
