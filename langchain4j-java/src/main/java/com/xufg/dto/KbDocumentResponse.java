package com.xufg.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文档响应数据。
 */
@Data
public class KbDocumentResponse {

    /** 文档 ID。 */
    private Long id;

    /** 文档名称。 */
    private String docName;

    /** 文件类型。 */
    private String fileType;

    /** 文件大小。 */
    private Long fileSize;

    /** 实际分段数。 */
    private Integer segmentCount;

    /** 处理状态。 */
    private Integer status;

    /** 失败原因。 */
    private String errorMsg;

    /** 上传用户名。 */
    private String uploadUserName;

    /** 创建时间。 */
    private LocalDateTime createdAt;
}
