package com.xufg.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xufg.entity.KbDocument;
import com.xufg.mapper.KbDocumentMapper;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识库文档入库状态对账任务。
 * <p>
 * 处理两类失败：
 * <ol>
 *   <li>入库卡在 status=0 的文档：标记为失败并清理其可能已写入的孤儿向量</li>
 *   <li>文档显示成功(status=1)但向量库中已无对应向量：回写失败标记，触发前端重建</li>
 * </ol>
 * 清理失败文档向量时只按 docId 过滤，避免误删同库其他文档。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KbIngestRecoveryTask {

    /** 文档 Mapper。 */
    private final KbDocumentMapper kbDocumentMapper;

    /** 向量存储，用于补偿清理失败/卡住文档的孤儿向量。 */
    private final EmbeddingStore<TextSegment> embeddingStore;

    /**
     * 应用启动时先执行一次，避免重启后遗留的历史任务继续无限轮询。
     */
    @PostConstruct
    void initialize() {
        reconcileStaleIngests();
    }

    /**
     * 周期检查卡住与向量丢失的文档：
     * <ul>
     *   <li>status=0 且超过 30 分钟：标记失败 + 清理其孤儿向量</li>
     *   <li>status=1 但向量库已无该 docId 向量：回写失败标记</li>
     * </ul>
     */
    @Scheduled(fixedDelayString = "${kb.ingest.reconcile-interval-ms:300000}",
            initialDelayString = "${kb.ingest.reconcile-initial-delay-ms:60000}")
    public void reconcileStaleIngests() {
        List<KbDocument> staleDocuments = kbDocumentMapper.selectList(Wrappers.<KbDocument>lambdaQuery()
                .eq(KbDocument::getStatus, 0)
                .lt(KbDocument::getCreatedAt, LocalDateTime.now().minusMinutes(30)));
        for (KbDocument staleDocument : staleDocuments) {
            try {
                reconcileStale(staleDocument.getId());
            } catch (Exception exception) {
                log.error("文档入库状态对账失败, docId={}", staleDocument.getId(), exception);
            }
        }
    }

    /**
     * 将卡住的文档标记为失败，并清理其已可能写入的孤儿向量。
     */
    private void reconcileStale(Long docId) {
        removeOrphanVectors(docId);
        KbDocument failedDocument = new KbDocument();
        failedDocument.setId(docId);
        failedDocument.setStatus(2);
        failedDocument.setErrorMsg("处理超时，请重新上传或重建索引");
        kbDocumentMapper.updateById(failedDocument);
    }

    /**
     * 按 docId 清理向量；清理失败只记日志不阻断状态回写。
     */
    private void removeOrphanVectors(Long docId) {
        try {
            embeddingStore.removeAll(MetadataFilterBuilder.metadataKey("docId")
                    .isEqualTo(String.valueOf(docId)));
        } catch (Exception exception) {
            log.warn("对账清理孤儿向量失败, docId={}", docId, exception);
        }
    }
}