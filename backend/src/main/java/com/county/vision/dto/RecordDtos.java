package com.county.vision.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

public class RecordDtos {

    @Data
    public static class ScreenRequest {
        @NotNull(message = "学生ID不能为空")
        private Long studentId;
        private LocalDate screenDate;
        private String orgName;
        private String operatorName;
        private String deviceCalibNo;
        private String nakedVisionLeft;
        private String nakedVisionRight;
        private String sphereLeft;
        private String sphereRight;
        private String cylinderLeft;
        private String cylinderRight;
        private Boolean wearingGlasses;
        private String habitSummary;
        private Boolean retestNeeded;
        private Boolean absent;
        /** 筛查场地（校医室/阶梯教室/操场等），用于按场地识别连续异常 */
        private String venue;
    }

    @Data
    public static class RetestInitiateRequest {
        /** 在原异常筛查记录上发起；与 studentId 二选一（传 studentId 时取最近一次异常首筛） */
        private Long originalScreenId;
        private Long studentId;
        /** 复测原因 RetestReason：DEVICE_BLOCK/WEAR_IRREGULAR/ENV_LIGHT/STATE_FLUCTUATION/OTHER */
        @NotNull(message = "复测原因不能为空")
        private String reason;
        /** 现场情况补充（设备遮挡/佩戴/光线的具体描述） */
        private String reasonDetail;
    }

    @Data
    public static class RetestSubmitRequest {
        @NotNull(message = "复测记录ID不能为空")
        private Long retestId;
        private LocalDate retestDate;
        private String orgName;
        private String operatorName;
        private String deviceCalibNo;
        private String venue;
        private String nakedVisionLeft;
        private String nakedVisionRight;
        private String sphereLeft;
        private String sphereRight;
        private String cylinderLeft;
        private String cylinderRight;
        private Boolean wearingGlasses;
        private String habitSummary;
        /** 复测结论补充说明（将同步到家长可见档案） */
        private String conclusionNote;
    }

    @Data
    public static class SchoolReviewRequest {
        @NotNull
        private Long studentId;
        /** CONFIRM / REJECT / RETEST */
        private String result;
        private String opinion;
    }

    @Data
    public static class NotifyRequest {
        @NotNull
        private Long studentId;
        private String channel;
        /** true=本次电话未接通（累计次数） */
        private Boolean unreachable;
        private String remark;
    }

    @Data
    public static class ParentChoiceRequest {
        @NotNull
        private Long studentId;
        /** COUNTY_HOSPITAL / PARTNER_CLINIC / DEFER */
        private String choice;
        private String targetOrg;
        private String deferReason;
        private String remark;
    }

    @Data
    public static class VisitRequest {
        @NotNull
        private Long studentId;
        private String orgName;
        private String doctor;
        private LocalDate visitDate;
        private Boolean trueMyopia;
        private Boolean needMydriasis;
        private String rxSphereLeft;
        private String rxSphereRight;
        private String rxCylinderLeft;
        private String rxCylinderRight;
        private String rxAxisLeft;
        private String rxAxisRight;
        private String pd;
        private String diagnosis;
        private Integer reviewCycleMonths;
        private Boolean subsidyEligible;
        private Boolean bigDifference;
        private String differenceNote;
    }

    @Data
    public static class GlassesRequest {
        @NotNull
        private Long studentId;
        private String opticianOrg;
        private LocalDate glassesDate;
        private Boolean partnerOptician;
        private java.math.BigDecimal amount;
        private String lensInfo;
        private Boolean discomfort;
        private String discomfortDetail;
    }

    @Data
    public static class SubsidyApplyRequest {
        @NotNull
        private Long studentId;
        private java.math.BigDecimal applyAmount;
        private String opinion;
    }

    @Data
    public static class SubsidyApproveRequest {
        @NotNull
        private Long applicationId;
        /** APPROVED / REJECTED / DISPUTE / PAID */
        private String action;
        private java.math.BigDecimal approvedAmount;
        private String opinion;
    }

    @Data
    public static class FollowUpRequest {
        @NotNull
        private Long studentId;
        private LocalDate plannedDate;
        private LocalDate actualDate;
        /** DONE / NO_SHOW */
        private String action;
        private String orgName;
        private String visionResult;
        private String remark;
    }

    @Data
    public static class InterventionRequest {
        @NotNull
        private Long studentId;
        /** 座位调整 SEAT_ADJUST 等 InterventionType */
        private String type;
        private LocalDate execDate;
        private String content;
        private Boolean executed;
    }

    @Data
    public static class IssueResolveRequest {
        @NotNull
        private Long issueId;
        private String resolveNote;
    }

    @Data
    public static class FixImportRequest {
        @NotNull
        private Long studentId;
        private String parentPhone;
        private String name;
        private String studentNo;
        private String grade;
        private String className;
        private String gender;
    }
}
