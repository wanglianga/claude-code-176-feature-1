package com.county.vision.web;

import com.county.vision.common.ApiResponse;
import com.county.vision.dto.RecordDtos;
import com.county.vision.service.RetestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 筛查异常二次确认（复测）接口。
 * 健康老师在原异常记录上发起复测、录入复测数据；平台按结论自动生成/关闭家长复诊提醒。
 */
@Tag(name = "筛查异常二次确认（复测）")
@RestController
@RequestMapping("/api/flow/retest")
public class RetestController {

    private final RetestService retestService;

    public RetestController(RetestService retestService) {
        this.retestService = retestService;
    }

    @Operation(summary = "在原异常筛查记录上发起二次复测（填写复测原因：设备遮挡/佩戴不规范/环境光线等）")
    @PostMapping("/initiate")
    public ApiResponse<Long> initiate(@Valid @RequestBody RecordDtos.RetestInitiateRequest req) {
        return ApiResponse.ok(retestService.initiate(req).getId());
    }

    @Operation(summary = "录入复测（第二次）数据并给出结论：仍异常→生成家长复诊提醒；恢复正常→关闭转诊任务")
    @PostMapping("/submit")
    public ApiResponse<Long> submit(@Valid @RequestBody RecordDtos.RetestSubmitRequest req) {
        return ApiResponse.ok(retestService.submit(req).getId());
    }
}
