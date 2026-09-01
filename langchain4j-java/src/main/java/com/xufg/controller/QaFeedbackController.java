package com.xufg.controller;

import com.xufg.common.Result;
import com.xufg.dto.FeedbackRequest;
import com.xufg.entity.QaFeedback;
import com.xufg.service.QaFeedbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 企业知识库问答反馈接口。
 */
@RestController
@RequiredArgsConstructor
public class QaFeedbackController {

    /** 问答反馈服务。 */
    private final QaFeedbackService qaFeedbackService;

    /**
     * 新增、更新或取消当前用户反馈。
     */
    @PostMapping("/api/feedback")
    public Result<Void> upsert(@Valid @RequestBody FeedbackRequest request) {
        qaFeedbackService.upsert(request.getHistoryId(), request.getRating(), request.getReason());
        return Result.ok();
    }

    /**
     * 回显当前用户对指定历史的反馈。
     */
    @GetMapping("/api/history/{historyId}/feedback")
    public Result<QaFeedback> get(@PathVariable Long historyId) {
        return Result.ok(qaFeedbackService.getByHistoryId(historyId));
    }
}
