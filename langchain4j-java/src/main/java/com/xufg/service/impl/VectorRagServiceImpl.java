package com.xufg.service.impl;

import com.xufg.service.VectorRagService;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentLoader;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.loader.ClassPathDocumentLoader;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.parser.apache.poi.ApachePoiDocumentParser;
import dev.langchain4j.data.document.source.FileSystemSource;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

@Service
public class VectorRagServiceImpl implements VectorRagService {
    // 自动注入PG向量存储
    @Resource
    private EmbeddingModel embeddingModel;

    @Resource
    private EmbeddingStore<TextSegment> embeddingStore;

    private static final ApachePdfBoxDocumentParser PDF_PARSER = new ApachePdfBoxDocumentParser();
    private static final ApachePoiDocumentParser WORD_PARSER = new ApachePoiDocumentParser();

    @Override
    public void storeText(String text) {
        // 将文本转换为向量
        Embedding embedding = embeddingModel.embed(text).content();
        // 创建 TextSegment
        TextSegment segment = TextSegment.from(text);
        // 存储
        embeddingStore.add(embedding, segment);
    }

    // 根据查询文本返回最相似的文本列表
    public List<String> findSimilarTexts(String query, int maxResults) {
        // 空查询没有检索意义，直接返回空结果，避免调用外部模型或误抛文件异常
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        // 演示接口对外暴露，必须钳制返回数量，防止一次请求拖垮向量库或泄露过多分段
        int normalizedMaxResults = Math.max(1, Math.min(50, maxResults));
        // 将查询文本转换为向量
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        // 在向量存储中查找最相似的片段
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(normalizedMaxResults)
                .build();

        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(searchRequest).matches();
        // 提取文本内容
        return matches.stream().map(m -> m.embedded().text()).toList();
    }

    @Override
    public String storeText4PDF(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename.equals(".")) {
            throw new RuntimeException("Original filename is empty");
        }
        String substring = originalFilename.substring(file.getOriginalFilename().lastIndexOf("."));
        if (!Arrays.asList(".pdf", ".docx", ".doc").contains(substring)) {
            throw new RuntimeException("Original filename is invalid");
        }
        Document document = parseMultipartFile(file);

        // 自定义元数据：保存文件名，后续检索可过滤文件
        document.metadata().put("fileName", file.getOriginalFilename());
        document.metadata().put("uploadTime", String.valueOf(System.currentTimeMillis()));

        // 2. 分段 + 批量向量化入库
        List<TextSegment> segments = splitAndStore(document);
        return "文件解析完成，入库片段数量：" + segments.size();
    }


    // 文档切割并写入PG向量库
    private List<TextSegment> splitAndStore(Document document) {
        // 递归分割器：200字符一段，重叠50字符防止上下文断裂（与 RagConfig 的灌库分割参数保持一致）
        var splitter = DocumentSplitters.recursive(200, 50);
        List<TextSegment> segments = splitter.split(document);

        List<Embedding> content = embeddingModel.embedAll(segments).content();

        // 底层调用阿里 Qwen 向量模型（application.yaml 中配置的 qwen3.7-text-embedding）生成向量，插入 postgres pgvector 表
        embeddingStore.addAll(content, segments);
        return segments;
    }

    /**
     * MultipartFile 解析为 LangChain Document
     */
    public static Document parseMultipartFile(MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename();
        InputStream inputStream = file.getInputStream();

        if (fileName.endsWith(".pdf")) {
            return PDF_PARSER.parse(inputStream);
        } else if (fileName.endsWith(".docx") || fileName.endsWith(".doc")) {
            return WORD_PARSER.parse(inputStream);
        } else {
            throw new RuntimeException("仅支持 pdf / docx / doc 文件");
        }
    }

    // 返回相似文本的数量
    public int countSimilarTexts(String query, int maxResults) {
        return findSimilarTexts(query, maxResults).size();
    }
}
