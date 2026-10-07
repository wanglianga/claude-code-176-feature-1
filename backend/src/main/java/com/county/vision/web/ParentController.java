package com.county.vision.web;

import com.county.vision.common.ApiResponse;
import com.county.vision.service.ParentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "家长可见档案")
@RestController
@RequestMapping("/api/parent")
public class ParentController {

    private final ParentService parentService;

    public ParentController(ParentService parentService) {
        this.parentService = parentService;
    }

    @Operation(summary = "家长查看自己孩子的健康档案：时间线、首次/复测两次数据、复测结论、复诊提醒通知留痕")
    @GetMapping("/my-child")
    public ApiResponse<Map<String, Object>> myChild() {
        return ApiResponse.ok(parentService.myChild());
    }
}
