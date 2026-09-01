package com.xufg.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xufg.common.BizException;
import com.xufg.entity.KbMember;
import com.xufg.enums.MemberRole;
import com.xufg.mapper.KbMemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 知识库成员权限校验服务。
 */
@Service
@RequiredArgsConstructor
public class KbPermissionService {

    /** 知识库成员 Mapper。 */
    private final KbMemberMapper kbMemberMapper;

    /**
     * 查询用户在知识库内的角色，非成员返回 null（不抛异常）。
     * <p>
     * 专供需要"捕获 403 后再按全局权限放行"的调用方使用：若在事务内捕获
     * {@link #getRole} 抛出的异常，参与事务会被标记 rollback-only 导致提交失败，
     * 因此这类场景必须走本方法以普通分支判断代替异常捕获。
     */
    public MemberRole findRoleOrNull(Long kbId, Long userId) {
        KbMember member = kbMemberMapper.selectOne(Wrappers.<KbMember>lambdaQuery()
                .eq(KbMember::getKbId, kbId)
                .eq(KbMember::getUserId, userId));
        return member == null ? null : MemberRole.valueOf(member.getMemberRole());
    }

    /**
     * 查询用户在知识库内的角色。
     */
    @Transactional(readOnly = true)
    public MemberRole getRole(Long kbId, Long userId) {
        KbMember member = kbMemberMapper.selectOne(Wrappers.<KbMember>lambdaQuery()
                .eq(KbMember::getKbId, kbId)
                .eq(KbMember::getUserId, userId));
        if (member == null) {
            throw new BizException(403, "无权访问该知识库");
        }
        return MemberRole.valueOf(member.getMemberRole());
    }

    /**
     * 校验用户至少具备指定库内角色， 并返回实际角色。
     */
    @Transactional(readOnly = true)
    public MemberRole assertMember(Long kbId, Long userId, MemberRole minRole) {
        MemberRole actualRole = getRole(kbId, userId);
        if (actualRole.getRank() < minRole.getRank()) {
            throw new BizException(403, "权限不足");
        }
        return actualRole;
    }
}
