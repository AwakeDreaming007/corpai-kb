package com.xufg.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 知识库成员角色， rank 值越大权限越高。
 */
@Getter
@RequiredArgsConstructor
public enum MemberRole {

    /** 库主， 拥有知识库全部管理权限。 */
    OWNER(3),

    /** 编辑者， 可以维护知识库内容。 */
    EDITOR(2),

    /** 查看者， 仅拥有只读权限。 */
    VIEWER(1);

    /** 角色权限等级。 */
    private final int rank;
}
