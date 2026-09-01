package com.xufg.config;

import com.alibaba.dashscope.tools.ToolAuthDashScopePlugin;
import dev.langchain4j.community.model.dashscope.QwenEmbeddingModel;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.DefaultMetadataStorageConfig;
import dev.langchain4j.store.embedding.pgvector.MetadataStorageMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import java.util.List;

@Order(0)
@Configuration
public class PgVectorConfig {

    // 阿里云 DashScope 向量模型配置（外部化到 application.yaml 的 langchain4j.dashscope 段，可用环境变量 DASHSCOPE_API_KEY 覆盖）
    @Value("${langchain4j.dashscope.api-key}")
    private String dashscopeApiKey;
    @Value("${langchain4j.dashscope.embedding-model-name}")
    private String embeddingModelName;
    @Value("${langchain4j.dashscope.embedding-dimension}")
    private int embeddingDimension;

    // pgvector 连接配置（外部化到 application.yaml 的 langchain4j.pgvector 段）
    @Value("${langchain4j.pgvector.host}")
    private String pgHost;
    @Value("${langchain4j.pgvector.port}")
    private int pgPort;
    @Value("${langchain4j.pgvector.database}")
    private String pgDatabase;
    @Value("${langchain4j.pgvector.user}")
    private String pgUser;
    @Value("${langchain4j.pgvector.password}")
    private String pgPassword;
    @Value("${langchain4j.pgvector.table}")
    private String pgTable;
    @Value("${langchain4j.pgvector.dimension}")
    private int pgDimension;

    /**
     * 阿里云 DashScope 嵌入模型（Qwen），用于文本向量化
     * @return 向量模型 Bean
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        return QwenEmbeddingModel.builder()
                .apiKey(dashscopeApiKey)
                .modelName(embeddingModelName)
                .dimension(embeddingDimension)
                .build();
    }

    /**
     * PostgreSQL pgvector 向量存储（表结构维度需与 embedding 维度一致）
     * @return 向量存储 Bean
     */
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        return PgVectorEmbeddingStore.builder()
                .host(pgHost)
                .port(pgPort)
                .database(pgDatabase)
                .user(pgUser)
                .password(pgPassword)
                .table(pgTable)
                .dimension(pgDimension)
                // 显式指定元数据保存为 JSON，防止默认配置漂移导致 metadata 列写入不一致
                // 注意：COMBINED_JSON 模式要求至少一个列定义（与 langchain4j 默认值、现有表结构对齐），传空列表会启动失败
                .metadataStorageConfig(DefaultMetadataStorageConfig.builder()
                        .storageMode(MetadataStorageMode.COMBINED_JSON)
                        .columnDefinitions(List.of("metadata JSON NULL"))
                        .indexes(List.of())
                        .build())
                .build();
    }
}
