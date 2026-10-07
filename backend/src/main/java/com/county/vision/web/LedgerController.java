package com.county.vision.web;

import com.county.vision.common.ApiResponse;
import com.county.vision.service.QueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "台账（异常事项/复查计划/补助/二次复测/转诊任务）")
@RestController
@RequestMapping("/api/ledger")
public class LedgerController {

    private final QueryService queryService;

    public LedgerController(QueryService queryService) {
        this.queryService = queryService;
    }

    @Operation(summary = "异常事项台账（OPEN/RESOLVED/ALL）")
    @GetMapping("/issues")
    public ApiResponse<List<Map<String, Object>>> issues(@RequestParam(required = false) String status) {
        return ApiResponse.ok(queryService.issues(status));
    }

    @Operation(summary = "复查随访台账（PLANNED/NO_SHOW/DONE/ALL）")
    @GetMapping("/followups")
    public ApiResponse<List<Map<String, Object>>> followups(@RequestParam(required = false) String status) {
        return ApiResponse.ok(queryService.followups(status));
    }

    @Operation(summary = "补助申请台账（按状态过滤）")
    @GetMapping("/subsidies")
    public ApiResponse<List<Map<String, Object>>> subsidies(@RequestParam(required = false) String status) {
        return ApiResponse.ok(queryService.subsidies(status));
    }

    @Operation(summary = "二次复测台账（PENDING待复测/ABNORMAL_CONFIRMED仍异常/NORMAL_CLOSED正常关闭/ALL），含两次数据、设备、操作人、原因")
    @GetMapping("/retests")
    public ApiResponse<List<Map<String, Object>>> retests(@RequestParam(required = false) String status) {
        return ApiResponse.ok(queryService.retests(status));
    }

    @Operation(summary = "家长复诊提醒（转诊任务）台账（PENDING/CONFIRMED/VISITED/CLOSED/ALL），关闭与已发通知均留痕不删除")
    @GetMapping("/referrals")
    public ApiResponse<List<Map<String, Object>>> referrals(@RequestParam(required = false) String status) {
        return ApiResponse.ok(queryService.referrals(status));
    }
}
