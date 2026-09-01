package com.xufg.service;

import com.xufg.entity.KbDocument;
import com.xufg.mapper.KbDocumentMapper;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * 知识库文档入库服务单元测试。
 */
@ExtendWith(MockitoExtension.class)
class KbIngestServiceTest {

    /** 文档 Mapper。 */
    @Mock
    private KbDocumentMapper kbDocumentMapper;

    /** 向量模型。 */
    @Mock
    private EmbeddingModel embeddingModel;

    /** 向量存储。 */
    @Mock
    private EmbeddingStore<TextSegment> embeddingStore;

    /** 事务模板。 */
    @Mock
    private TransactionTemplate transactionTemplate;

    /** 被测入库服务。 */
    @InjectMocks
    private KbIngestService kbIngestService;

    /**
     * 解析异常时状态置为失败并保存异常摘要。
     */
    @Test
    void shouldMarkFailedWhenParseThrows() throws Exception {
        Path documentFile = Path.of("target", "kb-ingest-test-broken.pdf");
        Files.createDirectories(documentFile.getParent());
        Files.writeString(documentFile, "pdf");
        KbDocument document = new KbDocument();
        document.setId(21L);
        document.setKbId(10L);
        document.setDocName("broken.pdf");
        document.setFileType("pdf");
        document.setFilePath(documentFile.toString());
        document.setChunkSize(200);
        document.setChunkOverlap(50);
        document.setStatus(0);
        when(kbDocumentMapper.selectById(21L)).thenReturn(document);

        ApachePdfBoxDocumentParser pdfParser = mock(ApachePdfBoxDocumentParser.class);
        when(pdfParser.parse(any(InputStream.class))).thenThrow(new RuntimeException("解析失败"));
        kbIngestService.pdfParser = pdfParser;
        doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        kbIngestService.ingest(21L);

        ArgumentCaptor<KbDocument> documentCaptor = ArgumentCaptor.forClass(KbDocument.class);
        verify(kbDocumentMapper).updateById(documentCaptor.capture());
        assertEquals(2, documentCaptor.getValue().getStatus());
        assertTrue(documentCaptor.getValue().getErrorMsg().contains("解析失败"));
        assertTrue(documentCaptor.getValue().getErrorMsg().length() <= 1000);
        verifyNoInteractions(embeddingModel);
        verify(embeddingStore).removeAll(any(Filter.class));
        verifyNoMoreInteractions(embeddingStore);
        Files.deleteIfExists(documentFile);
    }

    /**
     * 写入向量前发现文档行已消失时，必须丢弃向量。
     */
    @Test
    void shouldDiscardVectorsWhenDocumentDeletedBeforeAddAll() throws Exception {
        Path documentFile = prepareDocumentFile();
        KbDocument document = buildDocument(documentFile.toString());
        when(kbDocumentMapper.selectById(21L)).thenReturn(document).thenReturn(null);
        mockParserToReturnDocument();
        when(embeddingModel.embedAll(any())).thenReturn(Response.from(List.of(Embedding.from(new float[]{1F}))));

        kbIngestService.ingest(21L);

        verify(embeddingStore).removeAll(any(Filter.class));
        verify(embeddingStore, never()).addAll(any(), any());
        verify(kbDocumentMapper, never()).updateById(any(KbDocument.class));
        Files.deleteIfExists(documentFile);
    }

    /**
     * 写入向量后文档行消失时，必须立即补偿删除刚才写入的向量。
     */
    @Test
    void shouldRemoveOrphanVectorsWhenDocumentDeletedAfterAddAll() throws Exception {
        Path documentFile = prepareDocumentFile();
        KbDocument document = buildDocument(documentFile.toString());
        when(kbDocumentMapper.selectById(21L)).thenReturn(document).thenReturn(document).thenReturn(null);
        mockParserToReturnDocument();
        when(embeddingModel.embedAll(any())).thenReturn(Response.from(List.of(Embedding.from(new float[]{1F}))));

        kbIngestService.ingest(21L);

        verify(embeddingStore).addAll(any(), any());
        verify(embeddingStore, Mockito.times(2)).removeAll(any(Filter.class));
        verify(kbDocumentMapper, never()).updateById(any(KbDocument.class));
        Files.deleteIfExists(documentFile);
    }

    /**
     * 构造入库测试文件。
     */
    private Path prepareDocumentFile() throws Exception {
        Path documentFile = Path.of("target", "kb-ingest-test-valid.pdf");
        Files.createDirectories(documentFile.getParent());
        Files.writeString(documentFile, "pdf");
        return documentFile;
    }

    /**
     * 构造可解析的测试文档。
     */
    private KbDocument buildDocument(String filePath) {
        KbDocument document = new KbDocument();
        document.setId(21L);
        document.setKbId(10L);
        document.setDocName("valid.pdf");
        document.setFileType("pdf");
        document.setFilePath(filePath);
        document.setChunkSize(200);
        document.setChunkOverlap(50);
        document.setStatus(0);
        return document;
    }

    /**
     * 将 PDF 解析器替换为可直接返回文档的 Mock。
     */
    private void mockParserToReturnDocument() {
        ApachePdfBoxDocumentParser pdfParser = mock(ApachePdfBoxDocumentParser.class);
        when(pdfParser.parse(any(InputStream.class))).thenReturn(Document.from("测试内容"));
        kbIngestService.pdfParser = pdfParser;
    }
}
