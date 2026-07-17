package com.example.myspringai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Dify 知识库相关配置
 */
@Data
@ConfigurationProperties(prefix = "dify")
public class DifyProperties {

    /** API 配置 */
    private Api api = new Api();

    /** 知识库配置 */
    private Dataset dataset = new Dataset();

    @Data
    public static class Api {
        /** Dify 服务地址 */
        private String baseUrl = "http://localhost:3000";
        /** 知识库 API Key（在 Dify 知识库设置中获取） */
        private String key;
    }

    @Data
    public static class Dataset {
        /** 知识库 ID */
        private String id;
    }
}
