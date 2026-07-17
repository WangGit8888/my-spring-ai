package com.example.myspringai.service.impl;

import com.example.myspringai.config.DifyProperties;
import com.example.myspringai.model.DifyUploadResult;
import com.example.myspringai.service.DifyDocumentService;
import com.example.myspringai.service.UploadTaskTracker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dify 知识库文档服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DifyDocumentServiceImpl implements DifyDocumentService {

    private final DifyProperties difyProperties;
    private final RestTemplate difyRestTemplate;
    private final ObjectMapper objectMapper;
    private final UploadTaskTracker uploadTaskTracker;

    @Override
    @Async("difyUploadExecutor")
    public void uploadDocumentAsync(byte[] fileBytes, String fileName, String taskId) {
        try {
            uploadTaskTracker.markUploading(taskId);

            String url = buildUploadUrl();
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(difyProperties.getApi().getKey());

            MultiValueMap<String, Object> body = buildRequestBody(fileBytes, fileName);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = difyRestTemplate.exchange(
                    url, HttpMethod.POST, requestEntity, String.class);

            String responseBody = response.getBody();
            log.info("[{}] Dify 上传响应: {}", taskId, responseBody);

            DifyUploadResult result = parseResponse(responseBody, fileName);
            uploadTaskTracker.markCompleted(taskId, result);

        } catch (IllegalArgumentException e) {
            uploadTaskTracker.markFailed(taskId, e.getMessage());
        } catch (RestClientException e) {
            log.error("[{}] 上传文件到 Dify 失败: {}", taskId, e.getMessage(), e);
            uploadTaskTracker.markFailed(taskId, "上传到 Dify 失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("[{}] 未知异常: {}", taskId, e.getMessage(), e);
            uploadTaskTracker.markFailed(taskId, "未知异常: " + e.getMessage());
        }
    }

    /**
     * 构建 Dify API 上传地址
     * Dify Service API: POST /v1/datasets/{dataset_id}/document/create-by-file
     */
    private String buildUploadUrl() {
        String baseUrl = difyProperties.getApi().getBaseUrl().replaceAll("/+$", "");
        String datasetId = difyProperties.getDataset().getId();
        return baseUrl + "/v1/datasets/" + datasetId + "/document/create_by_file";
    }

    /**
     * 构建 multipart/form-data 请求体
     */
    private MultiValueMap<String, Object> buildRequestBody(byte[] fileBytes, String fileName) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

        // 文件部分 - 指定 Content-Type
        ByteArrayResource fileResource = new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };

        // 关键：添加 HttpEntity 包装，指定 Content-Type
        HttpEntity<ByteArrayResource> fileEntity = new HttpEntity<>(
                fileResource,
                HttpHeaders.EMPTY  // 或者 new HttpHeaders()
        );
        body.add("file", fileResource);

        // data 部分
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("indexing_technique", "high_quality");
            data.put("process_rule", Map.of("mode", "automatic"));
            String dataJson = objectMapper.writeValueAsString(data);

            // 关键：data 参数指定为 text/plain
            HttpHeaders dataHeaders = new HttpHeaders();
            dataHeaders.setContentType(MediaType.TEXT_PLAIN);
            HttpEntity<String> dataEntity = new HttpEntity<>(dataJson, dataHeaders);
            body.add("data", dataJson);
        } catch (Exception e) {
            throw new RuntimeException("构建请求数据失败", e);
        }

        return body;
    }

    /**
     * 解析 Dify API 响应
     */
    private DifyUploadResult parseResponse(String responseBody, String originalFilename) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            return DifyUploadResult.builder()
                    .documentId(root.path("document").path("id").asText())
                    .batchId(root.path("batch").asText())
                    .indexingStatus(root.path("document").path("indexing_status").asText("unknown"))
                    .documentName(originalFilename)
                    .rawMessage(responseBody)
                    .build();

        } catch (Exception e) {
            log.warn("解析 Dify 响应失败，返回原始信息: {}", responseBody, e);
            return DifyUploadResult.builder()
                    .documentName(originalFilename)
                    .rawMessage(responseBody)
                    .build();
        }
    }
}
