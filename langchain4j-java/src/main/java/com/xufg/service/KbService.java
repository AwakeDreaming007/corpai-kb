package com.xufg.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xufg.common.BizException;
import com.xufg.common.UserContext;
import com.xufg.dto.KbListResponse;
import com.xufg.entity.KbBase;
import com.xufg.entity.KbDocument;
import com.xufg.entity.KbMember;
import com.xufg.entity.SysUser;
import com.xufg.enums.MemberRole;
import com.xufg.mapper.KbBaseMapper;
import com.xufg.mapper.KbDocumentMapper;
import com.xufg.mapper.KbMemberMapper;
import com.xufg.mapper.SysUserMapper;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 知识库管理服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KbService {

    /** 知识库管理权限编码。 */
    private static final String KB_MANAGE_AUTHORITY = "kb:manage";

    /** 默认向量模型。 */
    private static final String DEFAULT_EMBEDDING_MODEL = "qwen3.7-text-embedding";

    /** 默认向量维度。 */
    private static final int DEFAULT_EMBEDDING_DIMENSION = 1536;

    /** 知识库 Mapper。 */
    private final KbBaseMapper kbBaseMapper;

    /** 知识库成员 Mapper。 */
    private final KbMemberMapper kbMemberMapper;

    /** 知识库文档 Mapper。 */
    private final KbDocumentMapper kbDocumentMapper;

    /** 用户 Mapper。 */
    private final SysUserMapper sysUserMapper;

    /** 库内权限校验服务。 */
    private final KbPermissionService kbPermissionService;

    /** 向量存储，仅用于删除知识库时清理向量。 */
    private final EmbeddingStore<TextSegment> embeddingStore;

    /** 编程式事务模板，用于把删除级联的数据库操作放在同一事务中。 */
    private final TransactionTemplate transactionTemplate;

    /**
     * 创建知识库，并同时创建库主成员关系。
     */
    @Transactional
    public Long create(String name, String description) {
        Long userId = UserContext.getUserId();
        assertNameAvailable(name, userId, null);

        KbBase kbBase = new KbBase();
        kbBase.setName(name);
        kbBase.setDescription(description);
        kbBase.setOwnerUserId(userId);
        kbBase.setEmbeddingModel(DEFAULT_EMBEDDING_MODEL);
        kbBase.setEmbeddingDimension(DEFAULT_EMBEDDING_DIMENSION);
        kbBase.setStatus(1);
        try {
            kbBaseMapper.insert(kbBase);
        } catch (DuplicateKeyException exception) {
            throw new BizException(400, "知识库名称已存在");
        }

        KbMember member = new KbMember();
        member.setKbId(kbBase.getId());
        member.setUserId(userId);
        member.setMemberRole(MemberRole.OWNER.name());
        kbMemberMapper.insert(member);
        return kbBase.getId();
    }

    /**
     * 分页查询当前用户可见的知识库。
     */
    @Transactional(readOnly = true)
    public Page<KbListResponse> list(Integer page, Integer size, String keyword) {
        long current = page == null ? 1L : Math.max(1, page);
        long pageSize = size == null ? 10L : Math.min(100L, Math.max(1, size));
        Long userId = UserContext.getUserId();

        List<KbMember> memberships = kbMemberMapper.selectList(Wrappers.<KbMember>lambdaQuery()
                .eq(KbMember::getUserId, userId));
        List<Long> sharedKbIds = memberships.stream().map(KbMember::getKbId).distinct().toList();
        // 注意：sharedKbIds 为空时不能调用 in()（MyBatis-Plus 会生成非法的 IN () SQL），
        // 因此仅在集合非空时拼接 OR 条件；无任何知识库的新用户走 owner 条件即可返回空页
        Page<KbBase> kbPage = kbBaseMapper.selectPage(new Page<>(current, pageSize),
                Wrappers.<KbBase>lambdaQuery()
                        .and(wrapper -> {
                            wrapper.eq(KbBase::getOwnerUserId, userId);
                            if (!sharedKbIds.isEmpty()) {
                                wrapper.or().in(KbBase::getId, sharedKbIds);
                            }
                        })
                        .like(StringUtils.hasText(keyword), KbBase::getName, keyword)
                        .orderByDesc(KbBase::getId));

        Set<Long> ownerUserIds = kbPage.getRecords().stream()
                .map(KbBase::getOwnerUserId)
                .collect(Collectors.toSet());
        Map<Long, SysUser> ownerById = ownerUserIds.isEmpty()
                ? Collections.emptyMap()
                : sysUserMapper.selectByIds(ownerUserIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        Map<Long, MemberRole> roleByKbId = memberships.stream()
                .collect(Collectors.toMap(KbMember::getKbId,
                        member -> MemberRole.valueOf(member.getMemberRole())));

        List<KbListResponse> records = kbPage.getRecords().stream()
                .map(kbBase -> toListResponse(kbBase, userId, ownerById, roleByKbId))
                .toList();
        Page<KbListResponse> responsePage = new Page<>(kbPage.getCurrent(), kbPage.getSize(), kbPage.getTotal());
        responsePage.setPages(kbPage.getPages());
        responsePage.setRecords(records);
        return responsePage;
    }

    /**
     * 更新知识库基础信息。
     */
    @Transactional
    public void update(Long kbId, String name, String description) {
        KbBase kbBase = requireKb(kbId);
        assertManageable(kbId);
        assertNameAvailable(name, kbBase.getOwnerUserId(), kbId);
        kbBase.setName(name);
        kbBase.setDescription(description);
        kbBaseMapper.updateById(kbBase);
    }

    /**
     * 删除知识库，并按向量、文件、数据库记录的顺序级联清理。
     */
    public void delete(Long kbId) {
        KbBase kbBase = requireKb(kbId);
        assertManageable(kbId);
        removeVectors(kbId);
        deleteDocumentFiles(kbId);
        deleteRecords(kbId);
    }

    /**
     * 转换知识库列表数据。
     */
    private KbListResponse toListResponse(KbBase kbBase,
                                          Long userId,
                                          Map<Long, SysUser> ownerById,
                                          Map<Long, MemberRole> roleByKbId) {
        boolean ownedByMe = userId.equals(kbBase.getOwnerUserId());
        MemberRole myRole = roleByKbId.get(kbBase.getId());
        if (myRole == null && ownedByMe) {
            myRole = MemberRole.OWNER;
        }
        KbListResponse response = new KbListResponse();
        response.setId(kbBase.getId());
        response.setName(kbBase.getName());
        response.setDescription(kbBase.getDescription());
        response.setOwnerUserId(kbBase.getOwnerUserId());
        response.setOwnerName(ownerById.getOrDefault(kbBase.getOwnerUserId(), new SysUser()).getUsername());
        response.setOwnedByMe(ownedByMe);
        response.setMyRole(myRole == null ? null : myRole.name());
        response.setCreatedAt(kbBase.getCreatedAt());
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
     * 校验库主或 kb:manage 权限。
     * <p>
     * 用 findRoleOrNull 做分支判断而非捕获 assertMember 异常：本方法在 update/delete
     * 事务内调用，若内层事务性校验抛异常被捕获，会因参与事务被标记 rollback-only 导致提交失败。
     */
    private void assertManageable(Long kbId) {
        Long userId = UserContext.getUserId();
        MemberRole role = kbPermissionService.findRoleOrNull(kbId, userId);
        if (role == MemberRole.OWNER) {
            return;
        }
        // kb:manage 全局管理权限始终放行，先于角色判断；不因用户同时是库成员而降权
        if (hasKbManageAuthority()) {
            return;
        }
        throw new BizException(403, role == null ? "无权访问该知识库" : "权限不足");
    }

    /**
     * 判断当前认证上下文是否持有 kb:manage 权限。
     */
    private boolean hasKbManageAuthority() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.getAuthorities().contains(new SimpleGrantedAuthority(KB_MANAGE_AUTHORITY));
    }

    /**
     * 校验同一库主下知识库名称唯一。
     */
    private void assertNameAvailable(String name, Long ownerUserId, Long excludeKbId) {
        Long count = kbBaseMapper.selectCount(Wrappers.<KbBase>lambdaQuery()
                .eq(KbBase::getOwnerUserId, ownerUserId)
                .eq(KbBase::getName, name)
                .ne(excludeKbId != null, KbBase::getId, excludeKbId));
        if (count != null && count > 0) {
            throw new BizException(400, "知识库名称已存在");
        }
    }

    /**
     * 清理知识库向量数据，失败时不阻断数据库级联删除。
     */
    private void removeVectors(Long kbId) {
        try {
            embeddingStore.removeAll(MetadataFilterBuilder.metadataKey("kbId")
                    .isEqualTo(String.valueOf(kbId)));
        } catch (Exception exception) {
            log.error("清理知识库向量失败, kbId={}", kbId, exception);
        }
    }

    /**
     * 删除文档磁盘文件，单个文件失败只记录日志。
     */
    private void deleteDocumentFiles(Long kbId) {
        List<KbDocument> documents = kbDocumentMapper.selectList(Wrappers.<KbDocument>lambdaQuery()
                .eq(KbDocument::getKbId, kbId));
        for (KbDocument document : documents) {
            String filePath = document.getFilePath();
            if (!StringUtils.hasText(filePath)) {
                continue;
            }
            try {
                Files.deleteIfExists(Path.of(filePath));
            } catch (RuntimeException | java.io.IOException exception) {
                log.error("删除知识库文档文件失败, kbId={}, filePath={}", kbId, filePath, exception);
            }
        }
    }

    /**
     * 在同一事务中删除文档、成员和知识库记录。
     */
    private void deleteRecords(Long kbId) {
        transactionTemplate.executeWithoutResult(status -> {
            kbDocumentMapper.delete(Wrappers.<KbDocument>lambdaQuery().eq(KbDocument::getKbId, kbId));
            kbMemberMapper.delete(Wrappers.<KbMember>lambdaQuery().eq(KbMember::getKbId, kbId));
            kbBaseMapper.deleteById(kbId);
        });
    }
}
