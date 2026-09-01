package com.xufg.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xufg.common.UserContext;
import com.xufg.enums.MemberRole;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 企业知识库手动编排流式 RAG 问答服务。
 */
@Service
public class KbChatService {

    /** 每次检索的最大分段数量。 */
    private static final int MAX_RESULTS = 5;

    /** 来源片段最大长度，避免 SSE 帧过长。 */
    private static final int SNIPPET_MAX_LENGTH = 200;

    /** 系统提示词。 */
    private static final String SYSTEM_PROMPT =
            "你是企业知识库助手，仅根据编号上下文回答，不知道就说不知道，回答用中文";

    /** 无命中时返回给用户的固定提示。 */
    private static final String NO_RESULT_MESSAGE = "未在知识库中找到相关内容";

    /** 历史落库使用的模型标识。 */
    private static final String CHAT_MODEL = "deepseek-stream";

    /** 向量模型，用于问题向量化。 */
    private final EmbeddingModel embeddingModel;

    /** pgvector 向量存储。 */
    private final EmbeddingStore<TextSegment> embeddingStore;

    /** DeepSeek 流式对话模型。 */
    private final StreamingChatModel streamingChatModel;

    /** Redis 持久化聊天记忆提供器。 */
    private final ChatMemoryProvider chatMemoryProvider;

    /** 库内权限校验服务。 */
    private final KbPermissionService kbPermissionService;

    /** 问答会话服务。 */
    private final QaSessionService qaSessionService;

    /** 问答历史服务，通过跨 Bean 调用保证 @Async 生效。 */
    private final QaHistoryService qaHistoryService;

    /** 检索最低相关性阈值。 */
    private final double minScore;

    /** Jackson JSON 序列化器，默认会正确转义换行等控制字符。 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 同会话写入锁；仅约束当前实例内并发，多实例部署仍需共享存储侧串行化。 */
    private final ConcurrentHashMap<String, Object> sessionLocks = new ConcurrentHashMap<>();

    /**
     * 使用 ChainConfig 中的指定 Bean 构建流式问答服务。
     */
    public KbChatService(EmbeddingModel embeddingModel,
                         EmbeddingStore<TextSegment> embeddingStore,
                         @Qualifier("dpAiStreamingChatModel") StreamingChatModel streamingChatModel,
                         @Qualifier("chatMemoryProvider_01") ChatMemoryProvider chatMemoryProvider,
                         KbPermissionService kbPermissionService,
                         QaSessionService qaSessionService,
                         QaHistoryService qaHistoryService,
                         @Value("${kb.retrieval.min-score:0.55}") String configuredMinScore) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.streamingChatModel = streamingChatModel;
        this.chatMemoryProvider = chatMemoryProvider;
        this.kbPermissionService = kbPermissionService;
        this.qaSessionService = qaSessionService;
        this.qaHistoryService = qaHistoryService;
        this.minScore = parseMinScore(configuredMinScore);
    }

    /**
     * 执行一次流式知识库问答。
     *
     * @param sessionId 会话 ID
     * @param kbId 知识库 ID
     * @param question 用户问题
     * @return SSE 事件流
     */
    public Flux<ServerSentEvent<String>> chat(String sessionId, Long kbId, String question) {
        long startTime = System.currentTimeMillis();
        Long userId = UserContext.getUserId();
        kbPermissionService.assertMember(kbId, userId, MemberRole.VIEWER);
        qaSessionService.requireOwnSession(sessionId, kbId);
        qaSessionService.updateTitleIfAbsent(sessionId, question);

        // embed/检索为外部调用（DashScope 模型 + pgvector），失败时返回 SSE error 事件而非 500
        List<EmbeddingMatch<TextSegment>> matches;
        try {
            Embedding queryEmbedding = embeddingModel.embed(question).content();
            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(MAX_RESULTS)
                    .minScore(minScore)
                    .filter(MetadataFilterBuilder.metadataKey("kbId").isEqualTo(String.valueOf(kbId)))
                    .build();
            matches = embeddingStore.search(searchRequest).matches();
        } catch (Exception exception) {
            String message = StringUtils.hasText(exception.getMessage())
                    ? exception.getMessage() : exception.getClass().getSimpleName();
            return Flux.just(sse("error", toJson(Map.of("message", message))));
        }

        if (matches.isEmpty()) {
            qaHistoryService.saveAsync(sessionId, kbId, userId, question,
                    NO_RESULT_MESSAGE, List.of(), CHAT_MODEL,
                    System.currentTimeMillis() - startTime);
            return Flux.just(
                    sse("token", toJson(Map.of("content", NO_RESULT_MESSAGE))),
                    sse("done", toJson(Map.of("sessionId", sessionId))));
        }

        List<Map<String, Object>> sources = buildSources(matches);
        ChatMemory memory = chatMemoryProvider.get(sessionId);
        Object sessionLock = sessionLocks.computeIfAbsent(sessionId, key -> new Object());

        // 仅把历史记忆拼入本轮上下文；原始问题不在这里入记忆，
        // 与回答一起在 onComplete/onError 成对写入，避免取消时残留单条问题
        List<ChatMessage> messages = new ArrayList<>();
        synchronized (sessionLock) {
            messages.add(SystemMessage.from(SYSTEM_PROMPT));
            messages.addAll(memory.messages());
            messages.add(UserMessage.from(buildUserPrompt(sources, question)));
        }

        return Flux.create(sink -> {
            AtomicBoolean completed = new AtomicBoolean(false);
            try {
                streamingChatModel.chat(messages, new StreamingChatResponseHandler() {
                    @Override
                    public void onPartialResponse(String token) {
                        if (sink.isCancelled()) {
                            return;
                        }
                        sink.next(sse("token", toJson(Map.of("content", token))));
                    }

                    @Override
                    public void onCompleteResponse(ChatResponse response) {
                        if (!completed.compareAndSet(false, true)) {
                            return;
                        }
                        String fullAnswer = response.aiMessage().text();
                        synchronized (sessionLock) {
                            memory.add(UserMessage.from(question));
                            memory.add(AiMessage.from(fullAnswer));
                        }
                        qaHistoryService.saveAsync(sessionId, kbId, userId, question,
                                fullAnswer, sources, CHAT_MODEL,
                                System.currentTimeMillis() - startTime);
                        if (!sink.isCancelled()) {
                            sink.next(sse("sources", toJson(sources)));
                            sink.next(sse("done", toJson(Map.of("sessionId", sessionId))));
                            sink.complete();
                        }
                    }

                    @Override
                    public void onError(Throwable error) {
                        if (!completed.compareAndSet(false, true)) {
                            return;
                        }
                        synchronized (sessionLock) {
                            memory.add(UserMessage.from(question));
                            memory.add(AiMessage.from("（本次回答失败）"));
                        }
                        qaHistoryService.saveAsync(sessionId, kbId, userId, question,
                                "（本次回答失败）", sources, CHAT_MODEL,
                                System.currentTimeMillis() - startTime);
                        if (sink.isCancelled()) {
                            return;
                        }
                        String message = StringUtils.hasText(error.getMessage())
                                ? error.getMessage() : error.getClass().getSimpleName();
                        sink.next(sse("error", toJson(Map.of("message", message))));
                        sink.complete();
                    }
                });
            } catch (Exception exception) {
                if (completed.compareAndSet(false, true) && !sink.isCancelled()) {
                    String message = StringUtils.hasText(exception.getMessage())
                            ? exception.getMessage() : exception.getClass().getSimpleName();
                    sink.next(sse("error", toJson(Map.of("message", message))));
                    sink.complete();
                }
            }
        }, FluxSink.OverflowStrategy.BUFFER);
    }

    /**
     * 将检索命中转换为前端可展示且可持久化的来源结构。
     */
    private List<Map<String, Object>> buildSources(List<EmbeddingMatch<TextSegment>> matches) {
        List<Map<String, Object>> sources = new ArrayList<>();
        int seq = 1;
        for (EmbeddingMatch<TextSegment> match : matches) {
            Metadata metadata = match.embedded().metadata();
            String text = match.embedded().text();
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("seq", seq++);
            source.put("docId", metadata.getString("docId"));
            source.put("docName", metadata.getString("fileName"));
            source.put("snippet", text.length() > SNIPPET_MAX_LENGTH
                    ? text.substring(0, SNIPPET_MAX_LENGTH) : text);
            source.put("score", match.score());
            sources.add(source);
        }
        return sources;
    }

    /**
     * 按编号拼接来源内容并组装当前用户提问。
     */
    private String buildUserPrompt(List<Map<String, Object>> sources, String question) {
        StringBuilder prompt = new StringBuilder("以下是知识库检索到的编号上下文：").append((char) 10);
        for (Map<String, Object> source : sources) {
            prompt.append('[').append(source.get("seq")).append("] 文件: ")
                    .append(source.get("docName")).append((char) 10)
                    .append("内容：").append(source.get("snippet"))
                    .append((char) 10).append((char) 10);
        }
        prompt.append("用户问题：").append(question);
        return prompt.toString();
    }

    /**
     * 构建 SSE 事件。
     */
    private ServerSentEvent<String> sse(String event, String data) {
        return ServerSentEvent.builder(data).event(event).build();
    }

    /**
     * 将对象序列化为 SSE data JSON。
     */
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("SSE JSON 序列化失败", exception);
        }
    }

    /**
     * 解析阈值配置，配置缺失、非法或为负数时使用默认值。
     */
    private double parseMinScore(String configuredMinScore) {
        try {
            double value = Double.parseDouble(configuredMinScore);
            return Double.isNaN(value) || value < 0D ? 0.55D : value;
        } catch (Exception exception) {
            return 0.55D;
        }
    }
}
