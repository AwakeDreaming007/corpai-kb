package com.xufg.service;

import com.xufg.entity.KbDocument;
import com.xufg.mapper.KbDocumentMapper;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.parser.apache.poi.ApachePoiDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 知识库文档异步解析入库服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KbIngestService {

    /** 文档 Mapper。 */
    private final KbDocumentMapper kbDocumentMapper;

    /** 向量模型，用于批量向量化分段。 */
    private final EmbeddingModel embeddingModel;

    /** 向量存储，用于批量写入分段。 */
    private final EmbeddingStore<TextSegment> embeddingStore;

    /** 编程式事务模板，用于状态短事务更新。 */
    private final TransactionTemplate transactionTemplate;

    /** PDF 解析器。 */
    ApachePdfBoxDocumentParser pdfParser = new ApachePdfBoxDocumentParser();

    /** Word 解析器。 */
    ApachePoiDocumentParser wordParser = new ApachePoiDocumentParser();

    /**
     * 异步解析文档并写入向量库；写入前后都检查文档状态，防止与删除/覆盖竞态产生孤儿向量。
     */
    @Async("kbIngestExecutor")
    public void ingest(Long docId) {
        try {
            embeddingStore.removeAll(buildDocIdFilter(docId));
        } catch (Exception exception) {
            log.error("清理上次文档向量失败, docId={}", docId, exception);
            updateStatusFailed(docId, exception);
            return;
        }

        KbDocument documentRecord = kbDocumentMapper.selectById(docId);
        if (documentRecord == null) {
            log.warn("文档不存在，跳过入库, docId={}", docId);
            return;
        }
        if (!Integer.valueOf(0).equals(documentRecord.getStatus())) {
            log.warn("文档不在处理中状态，跳过入库, docId={}, status={}", docId, documentRecord.getStatus());
            return;
        }

        try {
            List<TextSegment> segments = buildSegments(documentRecord);
            List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
            KbDocument currentRecord = kbDocumentMapper.selectById(docId);
            if (currentRecord == null || !Integer.valueOf(0).equals(currentRecord.getStatus())) {
                log.warn("向量写入前文档已删除或状态变化，丢弃向量, docId={}", docId);
                return;
            }
            embeddingStore.addAll(embeddings, segments);
            currentRecord = kbDocumentMapper.selectById(docId);
            if (currentRecord == null) {
                embeddingStore.removeAll(buildDocIdFilter(docId));
                log.warn("向量写入后文档已删除，已补偿清理, docId={}", docId);
                return;
            }
            updateStatusSuccess(documentRecord.getId(), segments.size());
        } catch (Exception exception) {
            log.error("文档入库失败, docId={}", docId, exception);
            updateStatusFailed(documentRecord.getId(), exception);
        }
    }

    /**
     * 异步重建文档向量；已在异步线程内直接复用解析入库流程。
     */
    @Async("kbIngestExecutor")
    public void reindex(Long docId) {
        try {
            embeddingStore.removeAll(buildDocIdFilter(docId));
        } catch (Exception exception) {
            log.error("清理旧文档向量失败, docId={}", docId, exception);
        }
        ingest(docId);
    }

    /**
     * 构建 docId 元数据过滤器。
     */
    private Filter buildDocIdFilter(Long docId) {
        return MetadataFilterBuilder.metadataKey("docId").isEqualTo(String.valueOf(docId));
    }

    /**
     * 解析文件、补充分段元数据并执行递归分段。
     */
    private List<TextSegment> buildSegments(KbDocument documentRecord) throws Exception {
        Document document = parseDocument(documentRecord);
        document.metadata().put("kbId", String.valueOf(documentRecord.getKbId()));
        document.metadata().put("docId", String.valueOf(documentRecord.getId()));
        document.metadata().put("fileName", documentRecord.getDocName());

        int chunkSize = documentRecord.getChunkSize() == null || documentRecord.getChunkSize() <= 0
                ? 200 : documentRecord.getChunkSize();
        int chunkOverlap = documentRecord.getChunkOverlap() == null || documentRecord.getChunkOverlap() < 0
                ? 50 : documentRecord.getChunkOverlap();
        List<TextSegment> segments = DocumentSplitters.recursive(chunkSize, chunkOverlap).split(document);
        for (TextSegment segment : segments) {
            segment.metadata().put("kbId", String.valueOf(documentRecord.getKbId()));
            segment.metadata().put("docId", String.valueOf(documentRecord.getId()));
            segment.metadata().put("fileName", documentRecord.getDocName());
        }
        return segments;
    }

    /**
     * 按文件扩展名选择 PDF 或 Word 解析器。
     */
    private Document parseDocument(KbDocument documentRecord) throws Exception {
        String fileType = documentRecord.getFileType() == null ? "" : documentRecord.getFileType().toLowerCase();
        try (InputStream inputStream = Files.newInputStream(Path.of(documentRecord.getFilePath()))) {
            if ("pdf".equals(fileType)) {
                return pdfParser.parse(inputStream);
            }
            if ("doc".equals(fileType) || "docx".equals(fileType)) {
                return wordParser.parse(inputStream);
            }
            throw new IllegalArgumentException("不支持的文档类型：" + fileType);
        }
    }

    /**
     * 在短事务中更新入库成功状态。
     */
    private void updateStatusSuccess(Long docId, int segmentCount) {
        transactionTemplate.executeWithoutResult(transactionStatus -> {
            KbDocument updateRecord = new KbDocument();
            updateRecord.setId(docId);
            updateRecord.setStatus(1);
            updateRecord.setSegmentCount(segmentCount);
            updateRecord.setErrorMsg("");
            kbDocumentMapper.updateById(updateRecord);
        });
    }

    /**
     * 在短事务中更新失败状态和异常摘要。
     */
    private void updateStatusFailed(Long docId, Exception exception) {
        transactionTemplate.executeWithoutResult(transactionStatus -> {
            KbDocument updateRecord = new KbDocument();
            updateRecord.setId(docId);
            updateRecord.setStatus(2);
            updateRecord.setErrorMsg(summarizeException(exception));
            kbDocumentMapper.updateById(updateRecord);
        });
    }

    /**
     * 生成数据库可保存的异常摘要。
     */
    private String summarizeException(Exception exception) {
        String message = exception.getMessage();
        String summary = message == null || message.isBlank()
                ? exception.getClass().getName()
                : exception.getClass().getName() + ": " + message;
        return summary.length() <= 1000 ? summary : summary.substring(0, 1000);
    }
}
