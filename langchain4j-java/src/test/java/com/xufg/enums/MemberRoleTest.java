package com.xufg.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 知识库成员角色等级单元测试。
 */
class MemberRoleTest {

    /**
     * 校验 OWNER >= EDITOR >= VIEWER 的等级顺序。
     */
    @Test
    void shouldCompareRolesByRank() {
        assertTrue(MemberRole.OWNER.getRank() >= MemberRole.EDITOR.getRank());
        assertTrue(MemberRole.EDITOR.getRank() >= MemberRole.VIEWER.getRank());
        assertEquals(3, MemberRole.OWNER.getRank());
        assertEquals(2, MemberRole.EDITOR.getRank());
        assertEquals(1, MemberRole.VIEWER.getRank());
    }
}
