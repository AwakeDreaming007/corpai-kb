package com.xufg.service;

import com.xufg.common.BizException;
import com.xufg.common.UserContext;
import com.xufg.entity.KbDocument;
import com.xufg.enums.MemberRole;
import com.xufg.mapper.KbDocumentMapper;
import com.xufg.mapper.SysUserMapper;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.transaction.TransactionStatus;
import org.springframework.core.task.TaskRejectedException;

/**
 * 知识库文档服务单元测试。
 */
@ExtendWith(MockitoExtension.class)
class KbDocumentServiceTest {

    /** 测试知识库 ID。 */
    private static final Long KB_ID = 10L;

    /** 测试用户 ID。 */
    private static final Long USER_ID = 1L;

    /** 文档 Mapper。 */
    @Mock
    private KbDocumentMapper kbDocumentMapper;

    /** 库内权限校验服务。 */
    @Mock
    private KbPermissionService kbPermissionService;

    /** 用户 Mapper。 */
    @Mock
    private SysUserMapper sysUserMapper;

    /** 异步入库服务。 */
    @Mock
    private KbIngestService kbIngestService;

    /** 向量存储。 */
    @Mock
    private EmbeddingStore<TextSegment> embeddingStore;

    /** 事务模板。 */
    @Mock
    private TransactionTemplate transactionTemplate;

    /** 被测文档服务。 */
    @InjectMocks
    private KbDocumentService kbDocumentService;

    /**
     * 清理当前用户上下文。
     */
    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    /**
     * 非法扩展名上传时拒绝。
     */
    @Test
    void shouldRejectInvalidFileType() {
        UserContext.set(USER_ID, "tester");
        when(kbPermissionService.assertMember(KB_ID, USER_ID, MemberRole.EDITOR)).thenReturn(MemberRole.EDITOR);
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", null,
                "content".getBytes(StandardCharsets.UTF_8));

        BizException exception = assertThrows(BizException.class, () -> kbDocumentService.upload(KB_ID, file));

        assertDoesNotThrow(() -> assertFalse(exception.getMessage().isBlank()));
        verifyNoInteractions(kbDocumentMapper, kbIngestService, embeddingStore, transactionTemplate);
    }

    /**
     * 空文件上传时拒绝。
     */
    @Test
    void shouldRejectEmptyFile() {
        UserContext.set(USER_ID, "tester");
        when(kbPermissionService.assertMember(KB_ID, USER_ID, MemberRole.EDITOR)).thenReturn(MemberRole.EDITOR);
        MockMultipartFile file = new MockMultipartFile("file", "a.pdf", null, new byte[0]);

        BizException exception = assertThrows(BizException.class, () -> kbDocumentService.upload(KB_ID, file));

        assertDoesNotThrow(() -> assertFalse(exception.getMessage().isBlank()));
        verifyNoInteractions(kbDocumentMapper, kbIngestService, embeddingStore, transactionTemplate);
    }

    /**
     * VIEWER 上传时权限异常向上传播。
     */
    @Test
    void shouldRejectUploadByViewer() {
        UserContext.set(USER_ID, "tester");
        when(kbPermissionService.assertMember(KB_ID, USER_ID, MemberRole.EDITOR))
                .thenThrow(new BizException(403, "权限不足"));
        MockMultipartFile file = new MockMultipartFile("file", "a.pdf", null,
                "content".getBytes(StandardCharsets.UTF_8));

        BizException exception = assertThrows(BizException.class, () -> kbDocumentService.upload(KB_ID, file));

        assertDoesNotThrow(() -> assertFalse(exception.getMessage().isBlank()));
        verifyNoInteractions(kbDocumentMapper, kbIngestService, embeddingStore, transactionTemplate);
    }

    /**
     * 同名覆盖时先提交新记录，提交成功后才销毁旧文件和旧向量。
     */
    @Test
    void shouldCleanupOldResourceOnlyAfterTransactionCommits() throws Exception {
        UserContext.set(USER_ID, "tester");
        when(kbPermissionService.assertMember(KB_ID, USER_ID, MemberRole.EDITOR)).thenReturn(MemberRole.EDITOR);

        Path uploadDir = Path.of("target", "kb-document-service-test");
        Files.createDirectories(uploadDir);
        Path oldFile = uploadDir.resolve("old.pdf");
        Files.writeString(oldFile, "old");
        kbDocumentService.uploadDir = uploadDir.toString();
        KbDocument oldDocument = new KbDocument();
        oldDocument.setId(20L);
        oldDocument.setKbId(KB_ID);
        oldDocument.setDocName("设计手册.pdf");
        oldDocument.setFilePath(oldFile.toString());
        when(kbDocumentMapper.selectOne(any())).thenReturn(oldDocument);

        doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        when(kbDocumentMapper.insert(any(KbDocument.class))).thenAnswer(invocation -> {
            KbDocument document = invocation.getArgument(0);
            document.setId(21L);
            return 1;
        });

        MockMultipartFile file = new MockMultipartFile("file", "设计手册.PDF", null,
                "content".getBytes(StandardCharsets.UTF_8));
        kbDocumentService.upload(KB_ID, file);

        InOrder inOrder = inOrder(kbDocumentMapper, embeddingStore, kbIngestService);
        inOrder.verify(kbDocumentMapper).deleteById(20L);
        inOrder.verify(kbDocumentMapper).insert(any(KbDocument.class));
        inOrder.verify(embeddingStore).removeAll(any(Filter.class));
        assertFalse(Files.exists(oldFile));
        inOrder.verify(kbIngestService).ingest(21L);
    }

    /**
     * 向量清理失败时覆盖上传必须整体中止，旧文件与新文件都不得销毁。
     */
    @Test
    void shouldAbortUploadOverrideWhenVectorCleanupFails() throws Exception {
        UserContext.set(USER_ID, "tester");
        when(kbPermissionService.assertMember(KB_ID, USER_ID, MemberRole.EDITOR)).thenReturn(MemberRole.EDITOR);
        Path uploadDir = Path.of("target", "kb-document-service-test");
        Files.createDirectories(uploadDir);
        Path oldFile = uploadDir.resolve("old.pdf");
        Files.writeString(oldFile, "old");
        kbDocumentService.uploadDir = uploadDir.toString();
        KbDocument oldDocument = new KbDocument();
        oldDocument.setId(20L);
        oldDocument.setKbId(KB_ID);
        oldDocument.setDocName("设计手册.pdf");
        oldDocument.setFilePath(oldFile.toString());
        when(kbDocumentMapper.selectOne(any())).thenReturn(oldDocument);
        doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        doThrow(new RuntimeException("pgvector 不可用")).when(embeddingStore).removeAll(any(Filter.class));

        MockMultipartFile file = new MockMultipartFile("file", "设计手册.PDF", null,
                "content".getBytes(StandardCharsets.UTF_8));
        BizException exception = assertThrows(BizException.class, () -> kbDocumentService.upload(KB_ID, file));

        assertEquals(503, exception.getCode());
        assertEquals("向量清理失败，请稍后重试", exception.getMessage());
        assertTrue(Files.exists(oldFile));
        verify(kbDocumentMapper).deleteById(20L);
        verify(kbDocumentMapper).insert(any(KbDocument.class));
        verifyNoInteractions(kbIngestService);
    }

    /**
     * 删除文档时向量清理失败必须中止，数据库行和文件都保留以便重试。
     */
    @Test
    void shouldAbortDeleteWhenVectorCleanupFails() throws Exception {
        UserContext.set(USER_ID, "tester");
        Path documentFile = Path.of("target", "kb-document-service-test", "delete.pdf");
        Files.createDirectories(documentFile.getParent());
        Files.writeString(documentFile, "document");
        KbDocument document = new KbDocument();
        document.setId(30L);
        document.setKbId(KB_ID);
        document.setFilePath(documentFile.toString());
        when(kbPermissionService.assertMember(KB_ID, USER_ID, MemberRole.EDITOR)).thenReturn(MemberRole.EDITOR);
        when(kbDocumentMapper.selectById(30L)).thenReturn(document);
        doThrow(new RuntimeException("pgvector 不可用")).when(embeddingStore).removeAll(any(Filter.class));

        BizException exception = assertThrows(BizException.class,
                () -> kbDocumentService.delete(KB_ID, 30L));

        assertEquals(503, exception.getCode());
        assertEquals("向量清理失败，请稍后重试", exception.getMessage());
        assertTrue(Files.exists(documentFile));
        verify(kbDocumentMapper, never()).deleteById(30L);
    }

    /**
     * 失败文档仍保留文件时必须允许重建索引。
     */
    @Test
    void shouldAllowReindexingFailedDocument() {
        UserContext.set(USER_ID, "tester");
        KbDocument document = new KbDocument();
        document.setId(30L);
        document.setKbId(KB_ID);
        document.setStatus(2);
        when(kbPermissionService.assertMember(KB_ID, USER_ID, MemberRole.EDITOR)).thenReturn(MemberRole.EDITOR);
        when(kbDocumentMapper.selectById(30L)).thenReturn(document);

        kbDocumentService.reindex(KB_ID, 30L);

        verify(kbIngestService).reindex(30L);
    }

    /**
     * 异步入库被线程池拒绝时文档必须标记失败并向用户返回明确错误。
     */
    @Test
    void shouldMarkUploadFailedWhenIngestRejected() throws Exception {
        UserContext.set(USER_ID, "tester");
        when(kbPermissionService.assertMember(KB_ID, USER_ID, MemberRole.EDITOR)).thenReturn(MemberRole.EDITOR);
        Path uploadDir = Path.of("target", "kb-document-service-test");
        Files.createDirectories(uploadDir);
        kbDocumentService.uploadDir = uploadDir.toString();
        doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        doThrow(new TaskRejectedException("系统繁忙")).when(kbIngestService).ingest(any());

        MockMultipartFile file = new MockMultipartFile("file", "设计手册.pdf", null,
                "content".getBytes(StandardCharsets.UTF_8));
        BizException exception = assertThrows(BizException.class, () -> kbDocumentService.upload(KB_ID, file));

        assertEquals(400, exception.getCode());
        assertEquals("系统繁忙，请稍后重试", exception.getMessage());
        ArgumentCaptor<KbDocument> documentCaptor = ArgumentCaptor.forClass(KbDocument.class);
        verify(kbDocumentMapper).insert(documentCaptor.capture());
        verify(kbDocumentMapper).updateById(documentCaptor.capture());
        assertEquals(2, documentCaptor.getValue().getStatus());
        assertEquals("系统繁忙，请稍后重试", documentCaptor.getValue().getErrorMsg());
    }
}
