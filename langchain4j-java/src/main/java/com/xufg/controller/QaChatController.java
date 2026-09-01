package com.xufg.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xufg.common.Result;
import com.xufg.dto.ChatRequest;
import com.xufg.entity.QaSession;
import com.xufg.service.KbChatService;
import com.xufg.service.QaSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * 企业知识库流式问答接口。
 */
@RestController
@RequestMapping("/api/kb")
@RequiredArgsConstructor
public class QaChatController {

    /** 问答会话服务。 */
    private final QaSessionService qaSessionService;

    /** 流式知识库问答服务。 */
    private final KbChatService kbChatService;

    /**
     * 创建问答会话。
     */
    @PostMapping("/{kbId}/sessions")
    public Result<String> createSession(@PathVariable Long kbId) {
        return Result.ok(qaSessionService.create(kbId));
    }

    /**
     * 分页查询当前用户在指定知识库内的问答会话。
     */
    @GetMapping("/{kbId}/sessions")
    public Result<Page<QaSession>> listSessions(@PathVariable Long kbId,
                                                @RequestParam(required = false) Integer page,
                                                @RequestParam(required = false) Integer size) {
        return Result.ok(qaSessionService.list(kbId, page, size));
    }

    /**
     * 建立流式问答连接。
     */
    @PostMapping(value = "/{kbId}/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(@PathVariable Long kbId,
                                              @Valid @RequestBody ChatRequest request) {
        return kbChatService.chat(request.getSessionId(), kbId, request.getQuestion());
    }
}
