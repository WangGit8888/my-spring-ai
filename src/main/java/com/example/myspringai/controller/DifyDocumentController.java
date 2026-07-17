package com.example.myspringai.controller;

import com.example.myspringai.model.UploadTaskInfo;
import com.example.myspringai.service.DifyDocumentService;
import com.example.myspringai.service.UploadTaskTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Dify 知识库文档接口
 */
@Slf4j
@RestController
@RequestMapping("/dify")
@RequiredArgsConstructor
public class DifyDocumentController {

    private final DifyDocumentService difyDocumentService;
    private final UploadTaskTracker uploadTaskTracker;

    /**
     * 上传文件到 Dify 知识库（异步）
     * 接口立即返回 taskId，实际上传在后台线程执行
     */
    @PostMapping("/document/upload")
    public ResponseEntity<?> uploadDocument(@RequestParam("file") MultipartFile file) {
        // 参数校验
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", 400,
                    "message", "上传文件不能为空"
            ));
        }

        try {
            // 先提取字节数组（MultipartFile 不能跨线程传递）
            byte[] fileBytes = file.getBytes();
            String fileName = file.getOriginalFilename();

            // 创建跟踪任务
            UploadTaskInfo task = uploadTaskTracker.createTask(fileName);

            // 丢给后台线程去上传 Dify
            difyDocumentService.uploadDocumentAsync(fileBytes, fileName, task.getTaskId());

            // 立即返回
            return ResponseEntity.ok(Map.of(
                    "code", 200,
                    "message", "文件已接收，正在后台异步上传到 Dify",
                    "data", Map.of(
                            "taskId", task.getTaskId(),
                            "status", task.getStatus()
                    )
            ));

        } catch (Exception e) {
            log.error("接收文件失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "code", 500,
                    "message", "接收文件失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 查询上传任务状态
     */
    @GetMapping("/document/status/{taskId}")
    public ResponseEntity<?> getUploadStatus(@PathVariable String taskId) {
        UploadTaskInfo task = uploadTaskTracker.getTask(taskId);

        if (task == null) {
            return ResponseEntity.notFound().build();
        }

        String message = switch (task.getStatus()) {
            case "PENDING" -> "任务排队中";
            case "UPLOADING" -> "正在上传到 Dify...";
            case "COMPLETED" -> "上传成功，Dify 正在向量化处理";
            case "FAILED" -> "上传失败";
            default -> task.getStatus();
        };

        return ResponseEntity.ok(Map.of(
                "code", 200,
                "message", message,
                "data", task
        ));
    }
}
