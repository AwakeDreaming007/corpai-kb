package com.xufg.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库列表数据。
 */
@Data
public class KbListResponse {

    /** 知识库 ID。 */
    private Long id;

    /** 知识库名称。 */
    private String name;

    /** 知识库描述。 */
    private String description;

    /** 库主用户 ID。 */
    private Long ownerUserId;

    /** 库主用户名。 */
    private String ownerName;

    /** 当前用户是否为库主。 */
    private Boolean ownedByMe;

    /** 当前用户库内角色。 */
    private String myRole;

    /** 创建时间。 */
    private LocalDateTime createdAt;
}
