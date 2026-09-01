package com.xufg.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xufg.common.Result;
import com.xufg.dto.QaHistoryDetailResponse;
import com.xufg.entity.QaHistory;
import com.xufg.entity.QaSession;
import com.xufg.service.QaHistoryService;
import com.xufg.service.QaSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 企业知识库问答历史接口。
 */
@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
public class QaHistoryController {

    /** 问答历史服务。 */
    private final QaHistoryService qaHistoryService;

    /** 问答会话服务。 */
    private final QaSessionService qaSessionService;

    /**
     * 分页查询当前用户在指定知识库内的会话。
     */
    @GetMapping("/sessions")
    public Result<Page<QaSession>> listSessions(@RequestParam Long kbId,
                                                @RequestParam(required = false) Integer page,
                                                @RequestParam(required = false) Integer size) {
        return Result.ok(qaSessionService.list(kbId, page, size));
    }

    /**
     * 分页查询当前用户的问答历史：按会话查询传 sessionId，按知识库浏览仅传 kbId。
     */
    @GetMapping
    public Result<Page<QaHistory>> list(@RequestParam(required = false) String sessionId,
                                        @RequestParam Long kbId,
                                        @RequestParam(required = false) Integer page,
                                        @RequestParam(required = false) Integer size) {
        if (sessionId != null && !sessionId.isBlank()) {
            return Result.ok(qaHistoryService.listBySession(sessionId, kbId, page, size));
        }
        return Result.ok(qaHistoryService.listByKb(kbId, page, size));
    }

    /**
     * 查询当前用户问答历史详情。
     */
    @GetMapping("/{id}")
    public Result<QaHistoryDetailResponse> get(@PathVariable Long id) {
        return Result.ok(qaHistoryService.detail(id));
    }

    /**
     * 删除当前用户问答历史。
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        qaHistoryService.delete(id);
        return Result.ok();
    }
}
