package com.xufg.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统用户。
 */
@Data
@TableName("sys_user")
public class SysUser {

    /** 用户 ID。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 登录用户名。 */
    @TableField("username")
    private String username;

    /** BCrypt 加密后的密码。 */
    @TableField("password")
    private String password;

    /** 用户昵称。 */
    @TableField("nickname")
    private String nickname;

    /** 用户状态： 1-启用， 0-禁用。 */
    @TableField("status")
    private Integer status;

    /** 创建时间。 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间。 */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
