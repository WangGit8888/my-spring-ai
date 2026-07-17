package com.example.myspringai.service;

import com.example.myspringai.model.DifyUploadResult;
import com.example.myspringai.model.UploadTaskInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 上传任务跟踪器（内存版，重启丢失）
 */
@Slf4j
@Component
public class UploadTaskTracker {

    private final ConcurrentHashMap<String, UploadTaskInfo> tasks = new ConcurrentHashMap<>();

    /**
     * 创建新任务，返回 PENDING 状态
     */
    public UploadTaskInfo createTask(String fileName) {
        String taskId = UUID.randomUUID().toString().substring(0, 8);
        UploadTaskInfo info = UploadTaskInfo.builder()
                .taskId(taskId)
                .fileName(fileName)
                .status("PENDING")
                .createTime(LocalDateTime.now())
                .build();
        tasks.put(taskId, info);
        log.info("创建上传任务: taskId={}, fileName={}", taskId, fileName);
        return info;
    }

    /**
     * 标记为上传中
     */
    public void markUploading(String taskId) {
        tasks.computeIfPresent(taskId, (k, v) -> {
            v.setStatus("UPLOADING");
            return v;
        });
    }

    /**
     * 标记为完成
     */
    public void markCompleted(String taskId, DifyUploadResult result) {
        tasks.computeIfPresent(taskId, (k, v) -> {
            v.setStatus("COMPLETED");
            v.setResult(result);
            v.setCompleteTime(LocalDateTime.now());
            log.info("上传任务完成: taskId={}, documentId={}", taskId, result.getDocumentId());
            return v;
        });
    }

    /**
     * 标记为失败
     */
    public void markFailed(String taskId, String errorMessage) {
        tasks.computeIfPresent(taskId, (k, v) -> {
            v.setStatus("FAILED");
            v.setErrorMessage(errorMessage);
            v.setCompleteTime(LocalDateTime.now());
            log.error("上传任务失败: taskId={}, error={}", taskId, errorMessage);
            return v;
        });
    }

    /**
     * 查询任务状态
     */
    public UploadTaskInfo getTask(String taskId) {
        return tasks.get(taskId);
    }
}
