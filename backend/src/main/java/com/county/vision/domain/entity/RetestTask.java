package com.county.vision.domain.entity;

import com.county.vision.common.BaseEntity;
import com.county.vision.domain.enums.RetestReason;
import com.county.vision.domain.enums.RetestStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 筛查异常二次确认（复测）任务：
 * 健康老师针对异常初测记录发起，保留初测/复测两次数据、设备编号、操作人员与复测原因；
 * 复测仍异常才生成家长复诊提醒，复测恢复正常则留下异常原因并关闭本次转诊任务。
 */
@Getter
@Setter
@Entity
@Table(name = "retest_task")
public class RetestTask extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Student student;

    /** 初测（被质疑的异常）筛查记录 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private ScreenRecord originalScreen;

    /** 复测筛查记录（完成后回填） */
    @ManyToOne(fetch = FetchType.LAZY)
    private ScreenRecord retestScreen;

    /** 发起复测的原因（设备遮挡/佩戴不规范/环境光线/现场组织/其他） */
    @Enumerated(EnumType.STRING)
    private RetestReason reason;

    /** 原因补充说明 */
    @Column(length = 500)
    private String reasonDetail;

    /** 发起人（健康老师） */
    private String initiator;

    @Enumerated(EnumType.STRING)
    private RetestStatus status = RetestStatus.PENDING;

    /** 复测结论（含初测异常归因说明） */
    @Column(length = 500)
    private String conclusion;

    private LocalDateTime finishTime;
}
