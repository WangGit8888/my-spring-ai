package com.example.myspringai.service;

/**
 * Dify 知识库文档服务
 */
public interface DifyDocumentService {

    /**
     * 异步上传文件到 Dify 知识库进行向量化。
     * Controller 调用后立即返回，实际上传在后台线程执行。
     *
     * @param fileBytes 文件字节数组（由 Controller 从 MultipartFile 提取）
     * @param fileName  原始文件名
     * @param taskId    任务追踪 ID
     */
    void uploadDocumentAsync(byte[] fileBytes, String fileName, String taskId);
}
