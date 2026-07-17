package com.example.myspringai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Dify 文档上传结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DifyUploadResult {

    /** Dify 文档ID */
    private String documentId;

    /** Dify 批次ID */
    private String batchId;

    /** 索引状态：waiting / indexing / completed / error */
    private String indexingStatus;

    /** 文档名称 */
    private String documentName;

    /** 原始响应信息（调试用） */
    private String rawMessage;
}
