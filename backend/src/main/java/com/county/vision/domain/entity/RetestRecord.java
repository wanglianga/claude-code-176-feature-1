package com.county.vision.domain.entity;

import com.county.vision.common.BaseEntity;
import com.county.vision.domain.enums.RetestReason;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 学生筛查异常的二次确认（复测）记录。
 * 健康老师在原异常筛查记录上发起复测：首次结果先不推送家长；
 * 平台完整保留两次筛查数据、两次的设备编号与操作人员、复测原因与复测结论。
 * 复测仍异常才生成家长复诊提醒；复测恢复正常则记录异常原因并关闭本次转诊任务。
 */
@Getter
@Setter
@Entity
@Table(name = "retest_record")
public class RetestRecord extends BaseEntity {

    public static final String STATUS_PENDING = "PENDING";                 // 已发起，待录入复测数据
    public static final String STATUS_ABNORMAL_CONFIRMED = "ABNORMAL_CONFIRMED"; // 复测仍异常
    public static final String STATUS_NORMAL_CLOSED = "NORMAL_CLOSED";   // 复测恢复正常，关闭转诊

    public static final String CONCLUSION_ABNORMAL = "ABNORMAL";
    public static final String CONCLUSION_NORMAL = "NORMAL";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Student student;

    /** 第一次（原始）筛查记录：在原记录上发起复测 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private ScreenRecord originalScreen;

    /** 第二次（复测）筛查记录，发起时为空，录入复测结果后生成 */
    @ManyToOne(fetch = FetchType.LAZY)
    private ScreenRecord retestScreen;

    /** 复测原因（设备遮挡/佩戴不规范/环境光线/状态波动等） */
    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private RetestReason reason;

    /** 复测原因补充说明（现场情况描述） */
    @Column(length = 500)
    private String reasonDetail;

    /** PENDING / ABNORMAL_CONFIRMED / NORMAL_CLOSED */
    @Column(length = 32, nullable = false)
    private String status = STATUS_PENDING;

    /** ABNORMAL / NORMAL，录入复测结果后按复测数据判定 */
    @Column(length = 16)
    private String conclusion;

    /** 复测结论说明（同步到家长可见档案） */
    @Column(length = 500)
    private String conclusionNote;

    private String initiatedBy;
    private LocalDateTime initiatedAt;

    /** 复测操作人/设备编号快照（与复测筛查记录一致，冗余便于台账展示） */
    private String retestOperatorName;
    private String retestDeviceCalibNo;
    private String retestOrgName;

    private String concludedBy;
    private LocalDateTime concludedAt;
}
