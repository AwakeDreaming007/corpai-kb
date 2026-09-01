package com.xufg.service;

import java.io.IOException;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xufg.common.BizException;
import com.xufg.common.UserContext;
import com.xufg.dto.KbDocumentResponse;
import com.xufg.entity.KbDocument;
import com.xufg.entity.SysUser;
import com.xufg.enums.MemberRole;
import com.xufg.mapper.KbDocumentMapper;
import com.xufg.mapper.SysUserMapper;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 知识库文档管理服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KbDocumentService {

    /** 允许上传的文件类型。 */
    private static final Set<String> ALLOWED_FILE_TYPES = Set.of("pdf", "doc", "docx");

    /** 文档 Mapper。 */
    private final KbDocumentMapper kbDocumentMapper;

    /** 库内权限校验服务。 */
    private final KbPermissionService kbPermissionService;

    /** 用户 Mapper。 */
    private final SysUserMapper sysUserMapper;

    /** 异步入库服务。 */
    private final KbIngestService kbIngestService;

    /** 向量存储，用于覆盖和删除文档时清理向量。 */
    private final EmbeddingStore<TextSegment> embeddingStore;

    /** 编程式事务模板。 */
    private final TransactionTemplate transactionTemplate;

    /** 文档落盘根目录。 */
    @Value("${kb.upload-dir}")
    String uploadDir;

    /**
     * 上传文档：覆盖同名旧文档后立即返回记录，并异步触发解析入库。
     */
    public KbDocumentResponse upload(Long kbId, MultipartFile file) {
        Long userId = UserContext.getUserId();
        kbPermissionService.assertMember(kbId, userId, MemberRole.EDITOR);
        validateUploadFile(file);

        String fileName = StringUtils.getFilename(file.getOriginalFilename());
        String fileType = extractFileType(fileName);
        Path targetPath = writeFile(kbId, fileType, file);
        KbDocument oldDocument = kbDocumentMapper.selectOne(Wrappers.<KbDocument>lambdaQuery()
                .eq(KbDocument::getKbId, kbId)
                .eq(KbDocument::getDocName, fileName));

        KbDocument document = new KbDocument();
        try {
            document.setKbId(kbId);
            document.setDocName(fileName);
            document.setFileType(fileType);
            document.setFileSize(file.getSize());
            document.setFilePath(targetPath.toString());
            document.setSplitterType("recursive");
            document.setChunkSize(200);
            document.setChunkOverlap(50);
            document.setSegmentCount(0);
            document.setStatus(0);
            document.setUploadUserId(userId);

            transactionTemplate.executeWithoutResult(transactionStatus -> {
                if (oldDocument != null) {
                    kbDocumentMapper.deleteById(oldDocument.getId());
                }
                kbDocumentMapper.insert(document);
            });
            if (oldDocument != null) {
                removeVectors(oldDocument.getId());
                cleanupFile(oldDocument.getFilePath());
            }
            kbIngestService.ingest(document.getId());
            return toResponse(document, UserContext.getUsername());
        } catch (DuplicateKeyException exception) {
            cleanupFile(targetPath.toString());
            throw new BizException(400, "文档正在处理中，请稍后重试");
        } catch (TaskRejectedException exception) {
            // Spring 将底层 RejectedExecutionException 包装为 TaskRejectedException（不同继承链），
            // 异常来自 ingest/reindex 的 @Async 提交；标记失败并返回明确提示，供前端可见并支持重试。
            markIngestFailed(document.getId(), "系统繁忙，请稍后重试");
            throw new BizException(400, "系统繁忙，请稍后重试");
        }
    }

    /**
     * 分页查询知识库文档，支持状态过滤。
     */
    public Page<KbDocumentResponse> list(Long kbId, Integer page, Integer size, Integer status) {
        kbPermissionService.assertMember(kbId, UserContext.getUserId(), MemberRole.VIEWER);
        long current = page == null ? 1L : Math.max(1, page);
        long pageSize = size == null ? 10L : Math.min(100L, Math.max(1, size));

        Page<KbDocument> documentPage = kbDocumentMapper.selectPage(new Page<>(current, pageSize),
                Wrappers.<KbDocument>lambdaQuery()
                        .eq(KbDocument::getKbId, kbId)
                        .eq(status != null, KbDocument::getStatus, status)
                        .orderByDesc(KbDocument::getId));

        Set<Long> userIds = documentPage.getRecords().stream()
                .map(KbDocument::getUploadUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> userNameById = userIds.isEmpty() ? Map.of() : sysUserMapper.selectBatchIds(userIds)
                .stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getUsername));
        List<KbDocumentResponse> records = documentPage.getRecords().stream()
                .map(document -> toResponse(document, userNameById.get(document.getUploadUserId())))
                .toList();
        Page<KbDocumentResponse> responsePage = new Page<>(documentPage.getCurrent(), documentPage.getSize(),
                documentPage.getTotal());
        responsePage.setPages(documentPage.getPages());
        responsePage.setRecords(records);
        return responsePage;
    }

    /**
     * 查询单个知识库文档，并校验跨库访问。
     */
    public KbDocumentResponse get(Long kbId, Long docId) {
        // 详情查询同样需要 VIEWER 及以上成员资格，防止非成员凭 kbId+docId 直读
        KbDocument document = requireDocument(kbId, docId, MemberRole.VIEWER);
        SysUser user = document.getUploadUserId() == null ? null : sysUserMapper.selectById(document.getUploadUserId());
        return toResponse(document, user == null ? null : user.getUsername());
    }

    /**
     * 删除文档，按向量、磁盘文件、数据库记录顺序清理。
     */
    public void delete(Long kbId, Long docId) {
        KbDocument document = requireDocument(kbId, docId, MemberRole.EDITOR);
        removeVectors(document.getId());
        cleanupFile(document.getFilePath());
        transactionTemplate.executeWithoutResult(transactionStatus -> kbDocumentMapper.deleteById(docId));
    }

    /**
     * 重建成功入库文档的向量数据。
     */
    public void reindex(Long kbId, Long docId) {
        KbDocument document = requireDocument(kbId, docId, MemberRole.EDITOR);
        if (!Integer.valueOf(1).equals(document.getStatus())
                && !Integer.valueOf(2).equals(document.getStatus())) {
            throw new BizException(400, "文档正在处理中，请稍后重试");
        }
        // 失败重试前先回到处理中状态，保证 ingest 内部的一致性检查可放行。
        KbDocument retryDocument = new KbDocument();
        retryDocument.setId(docId);
        retryDocument.setStatus(0);
        retryDocument.setErrorMsg("");
        kbDocumentMapper.updateById(retryDocument);
        kbIngestService.reindex(docId);
    }

    /**
     * 校验上传文件基础属性。
     */
    private void validateUploadFile(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw new BizException(400, "上传文件不能为空");
        }
    }

    /**
     * 从安全化后的文件名提取小写扩展名。
     */
    private String extractFileType(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            throw new BizException(400, "仅支持 PDF/DOC/DOCX 格式");
        }
        int extensionIndex = fileName.lastIndexOf('.');
        if (extensionIndex < 0 || extensionIndex == fileName.length() - 1) {
            throw new BizException(400, "仅支持 PDF/DOC/DOCX 格式");
        }
        String fileType = fileName.substring(extensionIndex + 1).toLowerCase();
        if (!ALLOWED_FILE_TYPES.contains(fileType)) {
            throw new BizException(400, "仅支持 PDF/DOC/DOCX 格式");
        }
        return fileType;
    }

    /**
     * 创建库目录并落盘新文件。
     */
    private Path writeFile(Long kbId, String fileType, MultipartFile file) {
        try {
            Path directory = Path.of(uploadDir, String.valueOf(kbId));
            Files.createDirectories(directory);
            Path targetPath = directory.resolve(UUID.randomUUID() + "." + fileType).toAbsolutePath();
            file.transferTo(targetPath);
            return targetPath;
        } catch (IOException exception) {
            throw new BizException(500, "文档上传失败");
        }
    }

    /**
     * 查询文档并校验其归属知识库与访问权限。
     */
    private KbDocument requireDocument(Long kbId, Long docId, MemberRole minRole) {
        kbPermissionService.assertMember(kbId, UserContext.getUserId(), minRole);
        return requireDocument(kbId, docId);
    }

    /**
     * 查询文档并校验其归属知识库。
     */
    private KbDocument requireDocument(Long kbId, Long docId) {
        KbDocument document = kbDocumentMapper.selectById(docId);
        if (document == null || !kbId.equals(document.getKbId())) {
            throw new BizException(404, "文档不存在");
        }
        return document;
    }

    /**
     * 清理文档向量；文档删除路径必须让向量清理成功后再删文件和数据库行。
     * 与整库删除的尽力清理不同，这里宁可让用户稍后重试，也不留下引用已删文档的孤儿向量。
     */
    private void removeVectors(Long docId) {
        try {
            embeddingStore.removeAll(MetadataFilterBuilder.metadataKey("docId")
                    .isEqualTo(String.valueOf(docId)));
        } catch (Exception exception) {
            log.error("清理文档向量失败, docId={}", docId, exception);
            throw new BizException(503, "向量清理失败，请稍后重试");
        }
    }

    /**
     * 将文档标记为入库失败，供异步调度被拒绝时前端可见并支持重试。
     */
    private void markIngestFailed(Long docId, String errorMessage) {
        KbDocument failedDocument = new KbDocument();
        failedDocument.setId(docId);
        failedDocument.setStatus(2);
        failedDocument.setErrorMsg(errorMessage);
        kbDocumentMapper.updateById(failedDocument);
    }

    /**
     * 清理磁盘文件，失败不阻断后续清理。
     */
    private void cleanupFile(String filePath) {
        if (!StringUtils.hasText(filePath)) {
            return;
        }
        try {
            Files.deleteIfExists(Path.of(filePath));
        } catch (Exception exception) {
            log.error("删除文档文件失败, filePath={}", filePath, exception);
        }
    }

    /**
     * 转换为接口响应数据，不返回文件路径。
     */
    private KbDocumentResponse toResponse(KbDocument document, String uploadUserName) {
        KbDocumentResponse response = new KbDocumentResponse();
        response.setId(document.getId());
        response.setDocName(document.getDocName());
        response.setFileType(document.getFileType());
        response.setFileSize(document.getFileSize());
        response.setSegmentCount(document.getSegmentCount());
        response.setStatus(document.getStatus());
        response.setErrorMsg(document.getErrorMsg());
        response.setUploadUserName(uploadUserName);
        response.setCreatedAt(document.getCreatedAt());
        return response;
    }
}
