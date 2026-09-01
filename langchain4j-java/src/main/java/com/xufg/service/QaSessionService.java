package com.xufg.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xufg.common.BizException;
import com.xufg.common.UserContext;
import com.xufg.entity.QaSession;
import com.xufg.enums.MemberRole;
import com.xufg.mapper.QaSessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 企业知识库问答会话服务。
 */
@Service
@RequiredArgsConstructor
public class QaSessionService {

    /** 会话标题最大截取长度。 */
    private static final int TITLE_MAX_LENGTH = 30;

    /** 问答会话 Mapper。 */
    private final QaSessionMapper qaSessionMapper;

    /** 库内权限校验服务。 */
    private final KbPermissionService kbPermissionService;

    /**
     * 创建当前用户在指定知识库内的问答会话。
     *
     * @param kbId 知识库 ID
     * @return 会话 ID，也作为 LangChain4j 的 memoryId
     */
    public String create(Long kbId) {
        Long userId = UserContext.getUserId();
        kbPermissionService.assertMember(kbId, userId, MemberRole.VIEWER);

        LocalDateTime now = LocalDateTime.now();
        QaSession session = new QaSession();
        session.setId(UUID.randomUUID().toString());
        session.setKbId(kbId);
        session.setUserId(userId);
        session.setCreatedAt(now);
        session.setLastActiveAt(now);
        qaSessionMapper.insert(session);
        return session.getId();
    }

    /**
     * 分页查询当前用户在指定知识库内的会话。
     *
     * @param kbId 知识库 ID
     * @param page 页码，从 1 开始
     * @param size 每页数量
     * @return 会话分页结果
     */
    public Page<QaSession> list(Long kbId, Integer page, Integer size) {
        Long userId = UserContext.getUserId();
        kbPermissionService.assertMember(kbId, userId, MemberRole.VIEWER);

        long current = page == null ? 1L : Math.max(1L, page);
        long pageSize = size == null ? 10L : Math.min(100L, Math.max(1L, size));
        return qaSessionMapper.selectPage(new Page<>(current, pageSize), Wrappers.<QaSession>lambdaQuery()
                .eq(QaSession::getKbId, kbId)
                .eq(QaSession::getUserId, userId)
                .orderByDesc(QaSession::getLastActiveAt));
    }

    /**
     * 校验当前用户自有会话并匹配知识库，同时刷新最近活跃时间。
     *
     * @param sessionId 会话 ID
     * @param kbId 知识库 ID
     * @return 当前会话
     */
    public QaSession requireOwnSession(String sessionId, Long kbId) {
        QaSession session = qaSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BizException(404, "会话不存在");
        }
        Long userId = UserContext.getUserId();
        if (!session.getUserId().equals(userId)) {
            throw new BizException(403, "无权访问该会话");
        }
        if (!session.getKbId().equals(kbId)) {
            throw new BizException(400, "会话与知识库不匹配");
        }

        session.setLastActiveAt(LocalDateTime.now());
        qaSessionMapper.updateById(session);
        return session;
    }

    /**
     * 首次提问时用问题前 30 个字符设置会话标题。
     *
     * @param sessionId 会话 ID
     * @param question 用户问题
     */
    public void updateTitleIfAbsent(String sessionId, String question) {
        QaSession session = qaSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BizException(404, "会话不存在");
        }
        if (StringUtils.hasText(session.getTitle())) {
            return;
        }

        session.setTitle(question.length() > TITLE_MAX_LENGTH
                ? question.substring(0, TITLE_MAX_LENGTH) : question);
        session.setLastActiveAt(LocalDateTime.now());
        qaSessionMapper.updateById(session);
    }
}
