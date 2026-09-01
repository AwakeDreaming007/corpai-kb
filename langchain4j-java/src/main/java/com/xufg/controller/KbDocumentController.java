package com.xufg.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xufg.common.Result;
import com.xufg.dto.KbDocumentResponse;
import com.xufg.service.KbDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库文档管理接口。
 */
@RestController
@RequestMapping("/api/kb/{kbId}/docs")
@RequiredArgsConstructor
public class KbDocumentController {

    /** 知识库文档服务。 */
    private final KbDocumentService kbDocumentService;

    /**
     * 上传知识库文档。
     */
    @PostMapping
    public Result<KbDocumentResponse> upload(@PathVariable Long kbId,
                                             @RequestParam("file") MultipartFile file) {
        return Result.ok(kbDocumentService.upload(kbId, file));
    }

    /**
     * 分页查询知识库文档。
     */
    @GetMapping
    public Result<Page<KbDocumentResponse>> list(@PathVariable Long kbId,
                                                 @RequestParam(required = false) Integer page,
                                                 @RequestParam(required = false) Integer size,
                                                 @RequestParam(required = false) Integer status) {
        return Result.ok(kbDocumentService.list(kbId, page, size, status));
    }

    /**
     * 查询知识库文档详情。
     */
    @GetMapping("/{docId}")
    public Result<KbDocumentResponse> get(@PathVariable Long kbId,
                                          @PathVariable Long docId) {
        return Result.ok(kbDocumentService.get(kbId, docId));
    }

    /**
     * 删除知识库文档。
     */
    @DeleteMapping("/{docId}")
    public Result<Void> delete(@PathVariable Long kbId,
                               @PathVariable Long docId) {
        kbDocumentService.delete(kbId, docId);
        return Result.ok();
    }

    /**
     * 重建知识库文档索引。
     */
    @PostMapping("/{docId}/reindex")
    public Result<Void> reindex(@PathVariable Long kbId,
                                @PathVariable Long docId) {
        kbDocumentService.reindex(kbId, docId);
        return Result.ok();
    }
}
