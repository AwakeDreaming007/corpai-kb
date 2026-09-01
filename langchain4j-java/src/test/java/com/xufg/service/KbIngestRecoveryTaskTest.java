package com.xufg.service;

import com.xufg.entity.KbDocument;
import com.xufg.mapper.KbDocumentMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文档入库状态对账任务单元测试。
 */
@ExtendWith(MockitoExtension.class)
class KbIngestRecoveryTaskTest {

    /** 文档 Mapper。 */
    @Mock
    private KbDocumentMapper kbDocumentMapper;

    /** 被测对账任务。 */
    @InjectMocks
    private KbIngestRecoveryTask recoveryTask;

    /**
     * 处理中的过期文档必须置为失败并提示重建索引。
     */
    @Test
    void shouldMarkStaleProcessingDocumentsFailed() {
        KbDocument staleDocument = new KbDocument();
        staleDocument.setId(21L);
        when(kbDocumentMapper.selectList(any())).thenReturn(List.of(staleDocument));

        recoveryTask.reconcileStaleIngests();

        ArgumentCaptor<KbDocument> captor = ArgumentCaptor.forClass(KbDocument.class);
        verify(kbDocumentMapper).updateById(captor.capture());
        assertEquals(21L, captor.getValue().getId());
        assertEquals(2, captor.getValue().getStatus());
        assertEquals("处理超时，请重新上传或重建索引", captor.getValue().getErrorMsg());
    }
}
