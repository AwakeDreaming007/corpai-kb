package com.xufg.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xufg.common.Result;
import com.xufg.dto.KbCreateRequest;
import com.xufg.dto.KbListResponse;
import com.xufg.dto.KbMemberAddRequest;
import com.xufg.dto.KbMemberResponse;
import com.xufg.dto.KbMemberRoleRequest;
import com.xufg.dto.KbUpdateRequest;
import com.xufg.service.KbMemberService;
import com.xufg.service.KbService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 知识库管理接口。
 */
@RestController
@RequestMapping("/api/kb")
@RequiredArgsConstructor
public class KbController {

    /** 知识库服务。 */
    private final KbService kbService;

    /** 知识库成员服务。 */
    private final KbMemberService kbMemberService;

    /**
     * 创建知识库。
     */
    @PostMapping
    @PreAuthorize("hasAuthority('kb:create')")
    public Result<Long> create(@Valid @RequestBody KbCreateRequest request) {
        return Result.ok(kbService.create(request.getName(), request.getDescription()));
    }

    /**
     * 分页查询当前用户可见的知识库。
     */
    @GetMapping
    public Result<Page<KbListResponse>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String keyword) {
        return Result.ok(kbService.list(page, size, keyword));
    }

    /**
     * 更新知识库基础信息。
     */
    @PutMapping("/{kbId}")
    public Result<Void> update(@PathVariable Long kbId, @Valid @RequestBody KbUpdateRequest request) {
        kbService.update(kbId, request.getName(), request.getDescription());
        return Result.ok();
    }

    /**
     * 删除知识库。
     */
    @DeleteMapping("/{kbId}")
    public Result<Void> delete(@PathVariable Long kbId) {
        kbService.delete(kbId);
        return Result.ok();
    }

    /**
     * 查询知识库成员列表。
     */
    @GetMapping("/{kbId}/members")
    public Result<List<KbMemberResponse>> listMembers(@PathVariable Long kbId) {
        return Result.ok(kbMemberService.listMembers(kbId));
    }

    /**
     * 添加知识库成员。
     */
    @PostMapping("/{kbId}/members")
    public Result<Void> addMember(@PathVariable Long kbId, @Valid @RequestBody KbMemberAddRequest request) {
        kbMemberService.addMember(kbId, request.getUsername(), request.getMemberRole());
        return Result.ok();
    }

    /**
     * 更新知识库成员角色。
     */
    @PutMapping("/{kbId}/members/{userId}")
    public Result<Void> updateMemberRole(@PathVariable Long kbId,
                                         @PathVariable Long userId,
                                         @Valid @RequestBody KbMemberRoleRequest request) {
        kbMemberService.updateMemberRole(kbId, userId, request.getMemberRole());
        return Result.ok();
    }

    /**
     * 移除知识库成员。
     */
    @DeleteMapping("/{kbId}/members/{userId}")
    public Result<Void> removeMember(@PathVariable Long kbId, @PathVariable Long userId) {
        kbMemberService.removeMember(kbId, userId);
        return Result.ok();
    }
}
