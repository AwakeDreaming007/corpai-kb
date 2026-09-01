package com.xufg.dto;

import lombok.Data;

/**
 * 权限项响应（供前端权限分配弹窗展示中文名并提交 ID）。
 */
@Data
public class PermissionResponse {

    /** 权限 ID。 */
    private Long id;

    /** 权限编码。 */
    private String permCode;

    /** 权限名称（中文展示）。 */
    private String permName;
}
