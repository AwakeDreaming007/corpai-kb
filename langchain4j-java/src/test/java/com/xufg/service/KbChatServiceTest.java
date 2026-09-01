package com.xufg.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xufg.common.UserContext;
import com.xufg.entity.QaSession;
import com.xufg.enums.MemberRole;
import com.xufg.service.QaHistoryService;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.BaseSubscriber;
import org.reactivestreams.Subscription;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

/**
 * 流式知识库问答服务单元测试。
 */
@ExtendWith(MockitoExtension.class)
class KbChatServiceTest {

    /** 向量模型。 */
    @Mock
    private EmbeddingModel embeddingModel;

    /** 向量存储。 */
    @Mock
    private EmbeddingStore<TextSegment> embeddingStore;

    /** DeepSeek 流式模型。 */
    @Mock
    private StreamingChatModel streamingChatModel;

    /** Redis 聊天记忆提供器。 */
    @Mock
    private ChatMemoryProvider chatMemoryProvider;

    /** 库内权限校验服务。 */
    @Mock
    private KbPermissionService kbPermissionService;

    /** 问答会话服务。 */
    @Mock
    private QaSessionService qaSessionService;

    /** 问答历史服务。 */
    @Mock
    private QaHistoryService qaHistoryService;

    /** 被测流式问答服务。 */
    private KbChatService kbChatService;

    /** JSON 序列化器。 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 初始化固定阈值的被测服务。
     */
    @BeforeEach
    void setUp() {
        kbChatService = new KbChatService(embeddingModel, embeddingStore, streamingChatModel,
                chatMemoryProvider, kbPermissionService, qaSessionService, qaHistoryService, "0.55");
    }

    /**
     * 清理当前用户上下文。
     */
    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    /**
     * 无命中时返回固定提示和完成事件，且完全不调用聊天模型。
     */
    @Test
    void shouldReturnFixedNoResultStreamWhenNoHits() throws Exception {
        prepareSearch(List.of());

        List<ServerSentEvent<String>> events = kbChatService
                .chat("session-1", 10L, "公司差旅标准是什么").collectList().block();

        assertEquals(2, events.size());
        assertEquals("token", events.get(0).event());
        assertEquals("done", events.get(1).event());
        JsonNode token = objectMapper.readTree(events.get(0).data());
        JsonNode done = objectMapper.readTree(events.get(1).data());
        assertEquals("未在知识库中找到相关内容", token.path("content").asText());
        assertEquals("session-1", done.path("sessionId").asText());
        verifyNoInteractions(streamingChatModel, chatMemoryProvider);
        verify(qaHistoryService).saveAsync(eq("session-1"), eq(10L), eq(1L),
                eq("公司差旅标准是什么"), eq("未在知识库中找到相关内容"), anyList(),
                eq("deepseek-stream"), anyLong());
        assertSearchRequest(0.55D);
    }

    /**
     * 有命中时按 token、sources、done 顺序输出，并完整写入会话记忆。
     */
    @Test
    void shouldStreamAnswerAndCompleteSources() throws Exception {
        Metadata metadata = Metadata.from("kbId", "10")
                .put("docId", "101")
                .put("fileName", "员工手册.pdf");
        TextSegment segment = TextSegment.from("员工差旅报销标准为经济舱。", metadata);
        EmbeddingMatch<TextSegment> match = new EmbeddingMatch<>(0.86D, "vector-1", null, segment);
        prepareSearch(List.of(match));

        ChatMemory chatMemory = Mockito.mock(ChatMemory.class);
        when(chatMemoryProvider.get("session-1")).thenReturn(chatMemory);
        when(chatMemory.messages()).thenReturn(List.of());
        doAnswer(invocation -> {
            StreamingChatResponseHandler handler = invocation.getArgument(1);
            handler.onPartialResponse("经济舱");
            handler.onCompleteResponse(ChatResponse.builder()
                    .aiMessage(AiMessage.from("经济舱可报销。")).build());
            return null;
        }).when(streamingChatModel).chat(anyList(), any(StreamingChatResponseHandler.class));

        List<ServerSentEvent<String>> events = kbChatService
                .chat("session-1", 10L, "差旅标准").collectList().block();

        assertEquals(3, events.size());
        assertEquals("token", events.get(0).event());
        assertEquals("sources", events.get(1).event());
        assertEquals("done", events.get(2).event());
        JsonNode sources = objectMapper.readTree(events.get(1).data());
        assertTrue(sources.isArray());
        assertEquals(1, sources.size());
        assertEquals(1, sources.get(0).path("seq").asInt());
        assertEquals("101", sources.get(0).path("docId").asText());
        assertEquals("员工手册.pdf", sources.get(0).path("docName").asText());
        assertEquals(0.86D, sources.get(0).path("score").asDouble());
        assertFalse(sources.get(0).path("snippet").asText().isEmpty());

        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMemory, Mockito.times(2)).add(messageCaptor.capture());
        assertEquals(ChatMessageType.USER, messageCaptor.getAllValues().get(0).type());
        assertEquals(ChatMessageType.AI, messageCaptor.getAllValues().get(1).type());
        verify(streamingChatModel).chat(anyList(), any(StreamingChatResponseHandler.class));
        verify(qaHistoryService).saveAsync(eq("session-1"), eq(10L), eq(1L),
                eq("差旅标准"), eq("经济舱可报销。"), anyList(),
                eq("deepseek-stream"), anyLong());
    }

    /**
     * SSE 客户端取消后，完整回答仍必须写入记忆和历史，防止下一轮问答记忆悬空。
     */
    @Test
    void shouldPersistMemoryAndHistoryWhenSseClientCancelled() {
        Metadata metadata = Metadata.from("kbId", "10")
                .put("docId", "101")
                .put("fileName", "员工手册.pdf");
        TextSegment segment = TextSegment.from("员工差旅报销标准为经济舱。", metadata);
        prepareSearch(List.of(new EmbeddingMatch<>(0.86D, "vector-1", null, segment)));

        ChatMemory chatMemory = Mockito.mock(ChatMemory.class);
        when(chatMemoryProvider.get("session-1")).thenReturn(chatMemory);
        when(chatMemory.messages()).thenReturn(List.of());
        doAnswer(invocation -> {
            StreamingChatResponseHandler handler = invocation.getArgument(1);
            handler.onCompleteResponse(ChatResponse.builder()
                    .aiMessage(AiMessage.from("经济舱可报销。")).build());
            return null;
        }).when(streamingChatModel).chat(anyList(), any(StreamingChatResponseHandler.class));

        AtomicBoolean subscribed = new AtomicBoolean(false);
        kbChatService.chat("session-1", 10L, "差旅标准").subscribe(
                new BaseSubscriber<ServerSentEvent<String>>() {
                    @Override
                    protected void hookOnSubscribe(Subscription subscription) {
                        subscribed.set(true);
                        cancel();
                    }

                    @Override
                    protected void hookOnNext(ServerSentEvent<String> value) {
                    }
                });

        assertTrue(subscribed.get());
        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMemory, Mockito.times(2)).add(messageCaptor.capture());
        assertEquals(ChatMessageType.AI, messageCaptor.getAllValues().get(1).type());
        verify(qaHistoryService).saveAsync(eq("session-1"), eq(10L), eq(1L),
                eq("差旅标准"), eq("经济舱可报销。"), anyList(),
                eq("deepseek-stream"), anyLong());
        verify(qaHistoryService, never()).saveAsync(eq("session-1"), eq(10L), eq(1L),
                eq("差旅标准"), eq(""), anyList(),
                eq("deepseek-stream"), anyLong());
    }

    /**
     * SSE 模型回调失败时也必须补写 AI 失败消息，保证同一轮记忆成对。
     */
    @Test
    void shouldPersistFailedAnswerWhenStreamingOnError() {
        Metadata metadata = Metadata.from("kbId", "10")
                .put("docId", "101")
                .put("fileName", "员工手册.pdf");
        TextSegment segment = TextSegment.from("员工差旅报销标准为经济舱。", metadata);
        prepareSearch(List.of(new EmbeddingMatch<>(0.86D, "vector-1", null, segment)));

        ChatMemory chatMemory = Mockito.mock(ChatMemory.class);
        when(chatMemoryProvider.get("session-1")).thenReturn(chatMemory);
        when(chatMemory.messages()).thenReturn(List.of());
        doAnswer(invocation -> {
            StreamingChatResponseHandler handler = invocation.getArgument(1);
            handler.onError(new RuntimeException("模型连接失败"));
            return null;
        }).when(streamingChatModel).chat(anyList(), any(StreamingChatResponseHandler.class));

        kbChatService.chat("session-1", 10L, "差旅标准").subscribe(event -> {
        });

        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMemory, Mockito.times(2)).add(messageCaptor.capture());
        assertEquals(ChatMessageType.USER, messageCaptor.getAllValues().get(0).type());
        assertEquals(ChatMessageType.AI, messageCaptor.getAllValues().get(1).type());
        verify(qaHistoryService).saveAsync(eq("session-1"), eq(10L), eq(1L),
                eq("差旅标准"), eq("（本次回答失败）"), anyList(),
                eq("deepseek-stream"), anyLong());
    }

    /**
     * 准备权限、会话与向量检索 Mock。
     */
    private void prepareSearch(List<EmbeddingMatch<TextSegment>> matches) {
        UserContext.set(1L, "tester");
        when(kbPermissionService.assertMember(10L, 1L, MemberRole.VIEWER)).thenReturn(MemberRole.VIEWER);
        QaSession session = new QaSession();
        session.setId("session-1");
        session.setKbId(10L);
        session.setUserId(1L);
        when(qaSessionService.requireOwnSession("session-1", 10L)).thenReturn(session);
        when(embeddingModel.embed(any(String.class)))
                .thenReturn(Response.from(Embedding.from(new float[]{1F})));
        when(embeddingStore.search(any(EmbeddingSearchRequest.class)))
                .thenReturn(new EmbeddingSearchResult<>(matches));
    }

    /**
     * 验证检索请求的最大条数、阈值与 kbId 过滤条件。
     */
    private void assertSearchRequest(double expectedMinScore) {
        ArgumentCaptor<EmbeddingSearchRequest> captor =
                ArgumentCaptor.forClass(EmbeddingSearchRequest.class);
        verify(embeddingStore).search(captor.capture());
        EmbeddingSearchRequest request = captor.getValue();
        assertEquals(5, request.maxResults());
        assertEquals(expectedMinScore, request.minScore());
        assertEquals(MetadataFilterBuilder.metadataKey("kbId").isEqualTo("10"), request.filter());
    }
}
