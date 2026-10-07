package com.county.vision.domain.entity;

import com.county.vision.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 转诊（家长复诊提醒）任务。
 * 只有学校复核确认、或二次复测仍异常时才生成；复测恢复正常时关闭而非删除，
 * 已发出的家长通知在档案中全程留痕。同一学生同一异常周期只允许存在一个未结束任务，
 * 避免重复创建复诊任务。
 */
@Getter
@Setter
@Entity
@Table(name = "referral_task", indexes = @Index(columnList = "student_id, status"))
public class ReferralTask extends BaseEntity {

    public static final String STATUS_PENDING = "PENDING";     // 已生成，待家长告知/确认
    public static final String STATUS_CONFIRMED = "CONFIRMED"; // 家长已确认并选择复诊机构
    public static final String STATUS_VISITED = "VISITED";     // 已完成医院复诊
    public static final String STATUS_CLOSED = "CLOSED";       // 关闭（复测恢复正常等），记录保留

    public static final String SOURCE_REVIEW = "REVIEW";       // 学校复核确认后生成
    public static final String SOURCE_RETEST = "RETEST";       // 二次复测仍异常后生成

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Student student;

    /** 由二次复测生成时关联复测记录 */
    @ManyToOne(fetch = FetchType.LAZY)
    private RetestRecord retestRecord;

    /** REVIEW / RETEST */
    @Column(length = 16, nullable = false)
    private String source;

    /** PENDING / CONFIRMED / VISITED / CLOSED */
    @Column(length = 16, nullable = false)
    private String status = STATUS_PENDING;

    /** 是否已向家长发出复诊通知 */
    private Boolean notified = false;
    private String notifyChannel;
    private LocalDateTime notifyTime;

    /** 家长选择的复诊机构（确认后回填） */
    private String targetOrg;

    @Column(length = 500)
    private String closeNote;
    private String closedBy;
    private LocalDateTime closedAt;

    private String createdBy;
}
