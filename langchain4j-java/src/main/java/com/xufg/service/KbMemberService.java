package com.xufg.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xufg.common.BizException;
import com.xufg.common.UserContext;
import com.xufg.dto.KbMemberResponse;
import com.xufg.entity.KbBase;
import com.xufg.entity.KbMember;
import com.xufg.entity.SysUser;
import com.xufg.enums.MemberRole;
import com.xufg.mapper.KbBaseMapper;
import com.xufg.mapper.KbMemberMapper;
import com.xufg.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 知识库成员管理服务。
 */
@Service
@RequiredArgsConstructor
public class KbMemberService {

    /** 知识库 Mapper。 */
    private final KbBaseMapper kbBaseMapper;

    /** 知识库成员 Mapper。 */
    private final KbMemberMapper kbMemberMapper;

    /** 用户 Mapper。 */
    private final SysUserMapper sysUserMapper;

    /** 库内权限校验服务。 */
    private final KbPermissionService kbPermissionService;

    /**
     * 查询知识库成员列表，并批量组装用户信息。
     */
    @Transactional(readOnly = true)
    public List<KbMemberResponse> listMembers(Long kbId) {
        requireKb(kbId);
        kbPermissionService.assertMember(kbId, UserContext.getUserId(), MemberRole.OWNER);

        List<KbMember> members = kbMemberMapper.selectList(Wrappers.<KbMember>lambdaQuery()
                .eq(KbMember::getKbId, kbId)
                .orderByAsc(KbMember::getId));
        List<Long> userIds = members.stream().map(KbMember::getUserId).distinct().toList();
        Map<Long, SysUser> userById = userIds.isEmpty()
                ? Collections.emptyMap()
                : sysUserMapper.selectByIds(userIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        return members.stream()
                .map(member -> toResponse(member, userById.get(member.getUserId())))
                .toList();
    }

    /**
     * 添加编辑者或查看者成员。
     */
    @Transactional
    public void addMember(Long kbId, String username, String memberRole) {
        requireKb(kbId);
        kbPermissionService.assertMember(kbId, UserContext.getUserId(), MemberRole.OWNER);
        MemberRole role = parseRole(memberRole);
        if (role == MemberRole.OWNER) {
            throw new BizException(400, "成员角色只允许 EDITOR 或 VIEWER");
        }

        List<SysUser> users = sysUserMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username)
                .last("LIMIT 1"));
        SysUser user = users.stream().findFirst()
                .orElseThrow(() -> new BizException(404, "用户不存在"));

        Long count = kbMemberMapper.selectCount(Wrappers.<KbMember>lambdaQuery()
                .eq(KbMember::getKbId, kbId)
                .eq(KbMember::getUserId, user.getId()));
        if (count != null && count > 0) {
            throw new BizException(400, "该用户已是成员");
        }

        KbMember member = new KbMember();
        member.setKbId(kbId);
        member.setUserId(user.getId());
        member.setMemberRole(role.name());
        try {
            kbMemberMapper.insert(member);
        } catch (DuplicateKeyException exception) {
            throw new BizException(400, "该用户已是成员");
        }
    }

    /**
     * 更新成员角色，并保证知识库唯一库主。
     */
    @Transactional
    public void updateMemberRole(Long kbId, Long userId, String newRole) {
        KbBase kbBase = requireKb(kbId);
        kbPermissionService.assertMember(kbId, UserContext.getUserId(), MemberRole.OWNER);
        MemberRole role = parseRole(newRole);
        KbMember target = kbMemberMapper.selectOne(Wrappers.<KbMember>lambdaQuery()
                .eq(KbMember::getKbId, kbId)
                .eq(KbMember::getUserId, userId));
        if (target == null) {
            throw new BizException(404, "成员不存在");
        }
        if (MemberRole.OWNER.name().equals(target.getMemberRole()) && role != MemberRole.OWNER) {
            throw new BizException(400, "请先转让库主");
        }

        if (role == MemberRole.OWNER && !MemberRole.OWNER.name().equals(target.getMemberRole())) {
            KbMember currentOwner = kbMemberMapper.selectOne(Wrappers.<KbMember>lambdaQuery()
                    .eq(KbMember::getKbId, kbId)
                    .eq(KbMember::getMemberRole, MemberRole.OWNER.name()));
            if (currentOwner == null) {
                throw new BizException(500, "知识库缺少库主");
            }
            currentOwner.setMemberRole(MemberRole.EDITOR.name());
            int demotedRows = kbMemberMapper.update(null, Wrappers.<KbMember>lambdaUpdate()
                    .eq(KbMember::getKbId, kbId)
                    .eq(KbMember::getMemberRole, MemberRole.OWNER.name())
                    .set(KbMember::getMemberRole, MemberRole.EDITOR.name()));
            if (demotedRows == 0) {
                throw new BizException(400, "成员已被并发修改，请刷新重试");
            }
        }

        target.setMemberRole(role.name());
        int updatedRows = kbMemberMapper.updateById(target);
        if (updatedRows == 0) {
            throw new BizException(400, "成员已被并发修改，请刷新重试");
        }

        if (role == MemberRole.OWNER) {
            kbBase.setOwnerUserId(userId);
            try {
                int kbUpdatedRows = kbBaseMapper.updateById(kbBase);
                if (kbUpdatedRows == 0) {
                    throw new BizException(400, "成员已被并发修改，请刷新重试");
                }
            } catch (DuplicateKeyException exception) {
                // 目标用户已有同名知识库，撞 uk_kb_base_owner_name(owner_user_id, name)
                throw new BizException(400, "目标用户已存在同名知识库，无法转让库主");
            }
        }
    }

    /**
     * 移除成员，并保护唯一库主。
     */
    @Transactional
    public void removeMember(Long kbId, Long userId) {
        requireKb(kbId);
        kbPermissionService.assertMember(kbId, UserContext.getUserId(), MemberRole.OWNER);
        KbMember target = kbMemberMapper.selectOne(Wrappers.<KbMember>lambdaQuery()
                .eq(KbMember::getKbId, kbId)
                .eq(KbMember::getUserId, userId));
        if (target == null) {
            throw new BizException(404, "成员不存在");
        }
        if (userId.equals(UserContext.getUserId())) {
            throw new BizException(400, "请先转让库主");
        }
        if (MemberRole.OWNER.name().equals(target.getMemberRole())) {
            Long ownerCount = kbMemberMapper.selectCount(Wrappers.<KbMember>lambdaQuery()
                    .eq(KbMember::getKbId, kbId)
                    .eq(KbMember::getMemberRole, MemberRole.OWNER.name()));
            if (ownerCount == null || ownerCount <= 1) {
                throw new BizException(400, "请先转让库主");
            }
        }
        kbMemberMapper.deleteById(target.getId());
    }

    /**
     * 转换成员列表数据。
     */
    private KbMemberResponse toResponse(KbMember member, SysUser user) {
        KbMemberResponse response = new KbMemberResponse();
        response.setId(member.getId());
        response.setUserId(member.getUserId());
        response.setUsername(user == null ? null : user.getUsername());
        response.setNickname(user == null ? null : user.getNickname());
        response.setMemberRole(member.getMemberRole());
        response.setCreatedAt(member.getCreatedAt());
        return response;
    }

    /**
     * 校验知识库存在并返回实体。
     */
    private KbBase requireKb(Long kbId) {
        KbBase kbBase = kbBaseMapper.selectById(kbId);
        if (kbBase == null) {
            throw new BizException(404, "知识库不存在");
        }
        return kbBase;
    }

    /**
     * 解析并校验库内角色。
     */
    private MemberRole parseRole(String role) {
        try {
            return MemberRole.valueOf(role);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BizException(400, "成员角色不合法");
        }
    }
}
