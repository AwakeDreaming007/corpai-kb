package com.xufg.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库成员。
 */
@Data
@TableName("kb_member")
public class KbMember {

    /** 成员 ID。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 知识库 ID。 */
    @TableField("kb_id")
    private Long kbId;

    /** 用户 ID。 */
    @TableField("user_id")
    private Long userId;

    /** 库内角色：OWNER / EDITOR / VIEWER。 */
    @TableField("member_role")
    private String memberRole;

    /** 加入时间。 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
