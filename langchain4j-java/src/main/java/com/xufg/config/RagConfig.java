package com.xufg.config;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.loader.ClassPathDocumentLoader;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.apache.poi.ApachePoiDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;

import java.util.List;

@Order(1)
@Configuration
public class RagConfig {

    @Resource
    public EmbeddingModel embeddingModel;

    @Resource
    public EmbeddingStore<TextSegment> embeddingStore;
    /**
     * 启动时把 classpath:content/ 下的文档灌入内存向量库（仅作演示，未传 embeddingModel，未真正向量化）。
     * 真正被 @AiService 的 contentRetriever 使用的是 PgVectorConfig 里的 embeddingStore Bean。
     * @return 内存向量库 Bean
     */
    @Bean
    @Profile("demo")
    public EmbeddingStore embeddingStore_1() {
        //加载文件
        List<Document> documents = ClassPathDocumentLoader.loadDocuments("content", new ApachePoiDocumentParser());
        //文档分割器
        DocumentSplitter recursive = DocumentSplitters.recursive(200, 50);
        InMemoryEmbeddingStore inMemoryEmbeddingStore = new InMemoryEmbeddingStore();
        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .embeddingStore(inMemoryEmbeddingStore)
                .documentSplitter(recursive)
                .build();
        ingestor.ingest(documents);

        return inMemoryEmbeddingStore;
    }

//    @Bean
//    public ContentRetriever contentRetriever(EmbeddingModel embeddingModel) {
//        // 1. 初始化内存向量存储（生产环境请替换为持久化存储，如 Milvus, Pinecone 等）[reference:12][reference:13]
//        EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
//
//        // 2. 加载你的文档并存入向量库
//        // List<Document> documents = ...;
//        // EmbeddingStoreIngestor.ingest(documents, embeddingStore, embeddingModel);[reference:14]
//
//        // 3. 构建基于向量库的检索器
//        return EmbeddingStoreContentRetriever.builder()
//                .embeddingStore(embeddingStore)
//                .embeddingModel(embeddingModel)
//                .build();
//    }

    @Bean
    public ContentRetriever contentRetriever(/*EmbeddingStore embeddingStore*/) {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .build();
    }
}
