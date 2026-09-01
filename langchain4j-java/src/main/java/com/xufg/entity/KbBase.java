package com.xufg.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库。
 */
@Data
@TableName("kb_base")
public class KbBase {

    /** 知识库 ID。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 知识库名称。 */
    @TableField("name")
    private String name;

    /** 知识库描述。 */
    @TableField("description")
    private String description;

    /** 库主用户 ID。 */
    @TableField("owner_user_id")
    private Long ownerUserId;

    /** 向量模型名称。 */
    @TableField("embedding_model")
    private String embeddingModel;

    /** 向量维度。 */
    @TableField("embedding_dimension")
    private Integer embeddingDimension;

    /** 状态：1 启用 / 0 停用。 */
    @TableField("status")
    private Integer status;

    /** 创建时间。 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间。 */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
