package com.county.vision.domain.entity;

import com.county.vision.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** 筛查机构现场记录 */
@Getter
@Setter
@Entity
@Table(name = "screen_record")
public class ScreenRecord extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Student student;

    private LocalDate screenDate;

    /** 筛查机构 / 操作人 */
    private String orgName;
    private String operatorName;

    /** 设备校准编号 */
    private String deviceCalibNo;

    /** 裸眼视力（小数记录，左右） */
    private String nakedVisionLeft;
    private String nakedVisionRight;

    /** 屈光度（等效球镜 D，左右） */
    private String sphereLeft;
    private String sphereRight;

    /** 散光（柱镜 D，左右） */
    private String cylinderLeft;
    private String cylinderRight;

    /** 筛查时是否已戴镜 */
    private Boolean wearingGlasses = false;

    /** 用眼习惯问卷摘要（每日近距离用眼时长、电子产品、户外活动等） */
    @Column(length = 1000)
    private String habitSummary;

    /** 是否需要复测 */
    private Boolean retestNeeded = false;

    /** 是否筛查异常（视力/屈光不达标） */
    private Boolean abnormal = false;

    /** 异常判定说明 */
    @Column(length = 500)
    private String abnormalReason;

    /** 当次请假未检 */
    private Boolean absent = false;

    /** 筛查轮次：SCREEN 初测 / RETEST 复测（二次确认） */
    private String screenRound = "SCREEN";
}
