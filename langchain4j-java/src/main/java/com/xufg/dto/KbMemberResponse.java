package com.xufg.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库成员列表数据。
 */
@Data
public class KbMemberResponse {

    /** 成员记录 ID。 */
    private Long id;

    /** 用户 ID。 */
    private Long userId;

    /** 登录用户名。 */
    private String username;

    /** 用户昵称。 */
    private String nickname;

    /** 库内角色。 */
    private String memberRole;

    /** 加入时间。 */
    private LocalDateTime createdAt;
}
