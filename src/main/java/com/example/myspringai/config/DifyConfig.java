package com.example.myspringai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.Executor;

/**
 * Dify 相关 Bean 配置
 */
@Configuration
@EnableAsync
@EnableConfigurationProperties(DifyProperties.class)
public class DifyConfig {

    /**
     * 异步上传用的线程池
     */
    @Bean
    public Executor difyUploadExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("dify-upload-");
        executor.initialize();
        return executor;
    }

    /**
     * 调用 Dify API 的 RestTemplate
     */
    @Bean
    public RestTemplate difyRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        // 连接超时 10 秒
        factory.setConnectTimeout(10_000);
        // 读取超时 120 秒（向量化处理可能需要较长时间）
        factory.setReadTimeout(120_000);
        return new RestTemplate(factory);
    }
}
