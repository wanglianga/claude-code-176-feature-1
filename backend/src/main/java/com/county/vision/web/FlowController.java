package com.county.vision.web;

import com.county.vision.common.ApiResponse;
import com.county.vision.domain.entity.*;
import com.county.vision.dto.RecordDtos;
import com.county.vision.service.FlowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

/**
 * 健康管理全流程操作接口。
 * 角色控制以业务可见性为主：前端按角色呈现功能；后端关键审批接口用方法级鉴权。
 */
@Tag(name = "健康管理全流程（筛查/复核/告知/确认/复诊/配镜/补助/复查/干预）")
@RestController
@RequestMapping("/api/flow")
public class FlowController {

    private final FlowService flow;

    public FlowController(FlowService flow) {
        this.flow = flow;
    }

    @Operation(summary = "筛查机构提交现场筛查记录（含请假、设备校准编号、用眼习惯、复测建议）")
    @PostMapping("/screen")
    public ApiResponse<Long> screen(@RequestBody RecordDtos.ScreenRequest req) {
        ScreenRecord r = flow.submitScreen(req);
        return ApiResponse.ok(r.getId());
    }

    @Operation(summary = "学校健康老师复核（CONFIRM确认/REJECT排除/RETEST复测）")
    @PostMapping("/school-review")
    public ApiResponse<Long> schoolReview(@RequestBody RecordDtos.SchoolReviewRequest req) {
        return ApiResponse.ok(flow.schoolReview(req).getId());
    }

    @Operation(summary = "发起筛查异常二次确认（复测）：保留初测记录，复测完成前不推送家长")
    @PostMapping("/retest/initiate")
    public ApiResponse<Long> initiateRetest(@RequestBody RecordDtos.RetestInitiateRequest req) {
        return ApiResponse.ok(flow.initiateRetest(req).getId());
    }

    @Operation(summary = "录入复测结果：复测仍异常生成家长复诊提醒（去重）；复测正常则关闭本次转诊任务（已发通知仅标记取消）")
    @PostMapping("/retest/complete")
    public ApiResponse<Long> completeRetest(@RequestBody RecordDtos.RetestCompleteRequest req) {
        return ApiResponse.ok(flow.completeRetest(req).getId());
    }

    @Operation(summary = "告知家长（可登记电话无法接通）")
    @PostMapping("/notify")
    public ApiResponse<Long> notify(@RequestBody RecordDtos.NotifyRequest req) {
        return ApiResponse.ok(flow.notifyParent(req).getId());
    }

    @Operation(summary = "家长确认并选择复诊机构/暂缓（暂缓必填原因）")
    @PostMapping("/parent-choice")
    public ApiResponse<Long> parentChoice(@RequestBody RecordDtos.ParentChoiceRequest req) {
        return ApiResponse.ok(flow.parentChoice(req).getId());
    }

    @Operation(summary = "医院/门诊回写复诊结果与处方（真性近视/散瞳/参数/复查周期/补助初判/差异登记）")
    @PostMapping("/visit")
    public ApiResponse<Long> visit(@RequestBody RecordDtos.VisitRequest req) {
        return ApiResponse.ok(flow.submitVisit(req).getId());
    }

    @Operation(summary = "配镜登记（合作机构或家长自行外配；可登记配镜后不适）")
    @PostMapping("/glasses")
    public ApiResponse<Long> glasses(@RequestBody RecordDtos.GlassesRequest req) {
        return ApiResponse.ok(flow.submitGlasses(req).getId());
    }

    @Operation(summary = "配镜后不适复诊调整，闭环不适事项")
    @PostMapping("/glasses/{id}/resolve-discomfort")
    public ApiResponse<Void> resolveDiscomfort(@PathVariable Long id, @RequestParam String note) {
        flow.resolveDiscomfort(id, note);
        return ApiResponse.ok();
    }

    @Operation(summary = "提交配镜补助申请")
    @PostMapping("/subsidy/apply")
    public ApiResponse<Long> applySubsidy(@RequestBody RecordDtos.SubsidyApplyRequest req) {
        return ApiResponse.ok(flow.applySubsidy(req).getId());
    }

    @Operation(summary = "卫健部门补助审批（APPRODED核准/PAID发放/REJECTED驳回/DISPUTE争议复核）")
    @PostMapping("/subsidy/approve")
    public ApiResponse<Long> approveSubsidy(@RequestBody RecordDtos.SubsidyApproveRequest req) {
        return ApiResponse.ok(flow.approveSubsidy(req).getId());
    }

    @Operation(summary = "复查随访登记（DONE完成/NO_SHOW未到场催办）")
    @PostMapping("/followup")
    public ApiResponse<Long> followUp(@RequestBody RecordDtos.FollowUpRequest req) {
        return ApiResponse.ok(flow.followUp(req).getId());
    }

    @Operation(summary = "学校日常干预登记（座位调整/户外活动/家长提醒/用眼指导）")
    @PostMapping("/intervention")
    public ApiResponse<Long> intervention(@RequestBody RecordDtos.InterventionRequest req) {
        return ApiResponse.ok(flow.addIntervention(req).getId());
    }

    @Operation(summary = "闭环异常事项")
    @PostMapping("/issue/resolve")
    public ApiResponse<Long> resolveIssue(@RequestBody RecordDtos.IssueResolveRequest req) {
        return ApiResponse.ok(flow.resolveIssue(req).getId());
    }
}
