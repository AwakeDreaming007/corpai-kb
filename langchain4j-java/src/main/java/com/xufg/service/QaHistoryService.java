package com.xufg.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xufg.common.BizException;
import com.xufg.common.UserContext;
import com.xufg.dto.QaHistoryDetailResponse;
import com.xufg.entity.QaHistory;
import com.xufg.mapper.QaHistoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 企业知识库问答历史服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QaHistoryService {

    /** 问答历史 Mapper。 */
    private final QaHistoryMapper qaHistoryMapper;

    /** Jackson JSON 序列化器，用于 sources 与 JSONB 列的转换。 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 异步保存一次问答历史。复用 kbIngestExecutor，避免为低频落库单独维护线程池；
     * 历史记录属于辅助数据，任何异常都只记录日志，不反传给 SSE 主流程。
     *
     * @param sessionId 问答会话 ID
     * @param kbId 知识库 ID
     * @param userId 提问用户 ID
     * @param question 用户原始问题
     * @param answer 模型最终回答
     * @param sources 引用来源
     * @param model 模型标识
     * @param latencyMs 回答耗时毫秒数
     */
    @Async("qaHistoryExecutor")
    public void saveAsync(String sessionId, Long kbId, Long userId, String question,
                          String answer, List<Map<String, Object>> sources,
                          String model, Long latencyMs) {
        try {
            QaHistory history = new QaHistory();
            history.setSessionId(sessionId);
            history.setKbId(kbId);
            history.setUserId(userId);
            history.setQuestion(question);
            history.setAnswer(answer);
            history.setSources(toJson(sources));
            history.setModel(model);
            history.setLatencyMs(latencyMs);
            history.setCreatedAt(LocalDateTime.now());
            qaHistoryMapper.insert(history);
        } catch (Exception exception) {
            log.error("问答历史落库失败, sessionId={}", sessionId, exception);
        }
    }

    /**
     * 分页查询当前用户指定会话的历史，并裁剪 answer/sources 大字段。
     *
     * @param sessionId 问答会话 ID
     * @param kbId 知识库 ID
     * @param page 页码
     * @param size 每页数量
     * @return 历史分页结果
     */
    public Page<QaHistory> listBySession(String sessionId, Long kbId,
                                         Integer page, Integer size) {
        if (!StringUtils.hasText(sessionId)) {
            throw new BizException(400, "sessionId 不能为空");
        }
        return qaHistoryMapper.selectPage(buildPage(page, size), Wrappers.<QaHistory>lambdaQuery()
                .select(QaHistory::getId, QaHistory::getSessionId, QaHistory::getKbId,
                        QaHistory::getUserId, QaHistory::getQuestion, QaHistory::getModel,
                        QaHistory::getLatencyMs, QaHistory::getCreatedAt)
                .eq(QaHistory::getSessionId, sessionId)
                .eq(kbId != null, QaHistory::getKbId, kbId)
                .eq(QaHistory::getUserId, UserContext.getUserId())
                .orderByDesc(QaHistory::getId));
    }

    /**
     * 分页查询当前用户指定知识库的历史，并裁剪 answer/sources 大字段。
     *
     * @param kbId 知识库 ID
     * @param page 页码
     * @param size 每页数量
     * @return 历史分页结果
     */
    public Page<QaHistory> listByKb(Long kbId, Integer page, Integer size) {
        return qaHistoryMapper.selectPage(buildPage(page, size), Wrappers.<QaHistory>lambdaQuery()
                .select(QaHistory::getId, QaHistory::getSessionId, QaHistory::getKbId,
                        QaHistory::getUserId, QaHistory::getQuestion, QaHistory::getModel,
                        QaHistory::getLatencyMs, QaHistory::getCreatedAt)
                .eq(QaHistory::getKbId, kbId)
                .eq(QaHistory::getUserId, UserContext.getUserId())
                .orderByDesc(QaHistory::getId));
    }

    /**
     * 查询当前用户问答历史详情，sources 解析为 JSON 数组返回。
     *
     * @param historyId 历史 ID
     * @return 历史详情
     */
    public QaHistoryDetailResponse detail(Long historyId) {
        QaHistory history = requireOwnHistory(historyId);
        QaHistoryDetailResponse response = new QaHistoryDetailResponse();
        response.setId(history.getId());
        response.setSessionId(history.getSessionId());
        response.setKbId(history.getKbId());
        response.setUserId(history.getUserId());
        response.setQuestion(history.getQuestion());
        response.setAnswer(history.getAnswer());
        response.setSources(parseSources(history.getSources()));
        response.setModel(history.getModel());
        response.setLatencyMs(history.getLatencyMs());
        response.setCreatedAt(history.getCreatedAt());
        return response;
    }

    /**
     * 删除当前用户问答历史；反馈记录由数据库级联删除。
     *
     * @param historyId 历史 ID
     */
    public void delete(Long historyId) {
        QaHistory history = requireOwnHistory(historyId);
        qaHistoryMapper.deleteById(history.getId());
    }

    /**
     * 查询本人历史，不存在返回 404，非本人返回 403。
     */
    private QaHistory requireOwnHistory(Long historyId) {
        QaHistory history = qaHistoryMapper.selectById(historyId);
        if (history == null) {
            throw new BizException(404, "问答历史不存在");
        }
        if (!history.getUserId().equals(UserContext.getUserId())) {
            throw new BizException(403, "无权访问");
        }
        return history;
    }

    /**
     * 构建钳制后的分页对象。
     */
    private Page<QaHistory> buildPage(Integer page, Integer size) {
        long current = page == null ? 1L : Math.max(1L, page);
        long pageSize = size == null ? 10L : Math.min(100L, Math.max(1L, size));
        return new Page<>(current, pageSize);
    }

    /**
     * 将来源列表序列化为 JSON 字符串。
     */
    private String toJson(List<Map<String, Object>> sources) {
        try {
            return objectMapper.writeValueAsString(sources == null ? List.of() : sources);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("问答来源序列化失败", exception);
        }
    }

    /**
     * 将 JSONB 文本解析为来源数组；异常数据按空数组兜底。
     */
    private List<Map<String, Object>> parseSources(String sources) {
        if (!StringUtils.hasText(sources)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(sources, new TypeReference<>() {
            });
        } catch (Exception exception) {
            log.warn("问答来源解析失败, sources={}", sources, exception);
            return List.of();
        }
    }
}
