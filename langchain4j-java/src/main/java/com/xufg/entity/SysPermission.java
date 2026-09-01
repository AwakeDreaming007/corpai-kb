package com.xufg.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统权限。
 */
@Data
@TableName("sys_permission")
public class SysPermission {

    /** 权限 ID。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 权限编码， 例如 kb:create。 */
    @TableField("perm_code")
    private String permCode;

    /** 权限名称。 */
    @TableField("perm_name")
    private String permName;

    /** 权限分组。 */
    @TableField("perm_group")
    private String permGroup;

    /** 权限描述。 */
    @TableField("description")
    private String description;

    /** 创建时间。 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间。 */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
