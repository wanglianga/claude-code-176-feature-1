package com.county.vision.domain.entity;

import com.county.vision.common.BaseEntity;
import com.county.vision.domain.enums.ParentChoice;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 家长对筛查异常结果的确认与复诊选择 */
@Getter
@Setter
@Entity
@Table(name = "parent_confirmation")
public class ParentConfirmation extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Student student;

    /** 告知方式：短信/电话/APP/家长会 */
    private String notifyChannel;
    private LocalDateTime notifyTime;
    private Boolean notified = false;
    /** 电话无法接通次数 */
    private Integer unreachableCount = 0;

    private Boolean confirmed = false;
    private LocalDateTime confirmTime;
    private String confirmerName;

    @Enumerated(EnumType.STRING)
    private ParentChoice choice;

    /** 选择的具体复诊机构名 */
    private String targetOrg;

    /** 暂缓原因（choice=DEFER 时必填） */
    @Column(length = 500)
    private String deferReason;

    private String remark;

    /**
     * 复诊任务是否已取消（如复测恢复正常后关闭转诊）。
     * 已发出的告知不做物理删除，仅标记取消并保留原因，家长可见档案中仍可追溯。
     */
    private Boolean cancelled = false;

    @Column(length = 500)
    private String cancelReason;

    private LocalDateTime cancelTime;
}
