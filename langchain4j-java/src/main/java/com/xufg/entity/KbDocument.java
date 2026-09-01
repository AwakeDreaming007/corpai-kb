package com.xufg.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文档。
 */
@Data
@TableName("kb_document")
public class KbDocument {

    /** 文档 ID。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 知识库 ID。 */
    @TableField("kb_id")
    private Long kbId;

    /** 文档名称。 */
    @TableField("doc_name")
    private String docName;

    /** 文件类型。 */
    @TableField("file_type")
    private String fileType;

    /** 文件大小。 */
    @TableField("file_size")
    private Long fileSize;

    /** 文件落盘路径。 */
    @TableField("file_path")
    private String filePath;

    /** 分割器类型。 */
    @TableField("splitter_type")
    private String splitterType;

    /** 分段大小。 */
    @TableField("chunk_size")
    private Integer chunkSize;

    /** 分段重叠长度。 */
    @TableField("chunk_overlap")
    private Integer chunkOverlap;

    /** 实际分段数。 */
    @TableField("segment_count")
    private Integer segmentCount;

    /** 处理状态：0 处理中 / 1 成功 / 2 失败。 */
    @TableField("status")
    private Integer status;

    /** 解析失败原因。 */
    @TableField("error_msg")
    private String errorMsg;

    /** 上传用户 ID。 */
    @TableField("upload_user_id")
    private Long uploadUserId;

    /** 创建时间。 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间。 */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
