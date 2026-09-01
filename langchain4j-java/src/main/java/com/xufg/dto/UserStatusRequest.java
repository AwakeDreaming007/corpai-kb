package com.xufg.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 用户状态更新请求。
 */
@Data
public class UserStatusRequest {

    /** 目标状态：1 启用， 0 禁用。 */
    @NotNull(message = "状态不能为空")
    @Min(value = 0, message = "状态只能为 0 或 1")
    @Max(value = 1, message = "状态只能为 0 或 1")
    private Integer status;
}
