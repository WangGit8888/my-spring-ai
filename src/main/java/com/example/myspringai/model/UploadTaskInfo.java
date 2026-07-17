package com.example.myspringai.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 异步上传任务状态
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UploadTaskInfo {

    /** 任务ID */
    private String taskId;

    /** 文件名 */
    private String fileName;

    /** 任务状态：PENDING / UPLOADING / COMPLETED / FAILED */
    private String status;

    /** 上传结果（仅 COMPLETED 时有值） */
    private DifyUploadResult result;

    /** 错误信息（仅 FAILED 时有值） */
    private String errorMessage;

    /** 任务创建时间 */
    private LocalDateTime createTime;

    /** 任务完成时间 */
    private LocalDateTime completeTime;
}
