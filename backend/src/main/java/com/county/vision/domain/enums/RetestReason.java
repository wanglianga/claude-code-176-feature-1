package com.county.vision.domain.enums;

/**
 * 筛查异常二次确认（复测）的发起原因：
 * 用于区分学生个体问题与设备、光线、现场组织等外部因素
 */
public enum RetestReason {
    DEVICE_BLOCK("设备遮挡/故障"),
    WEARING_IMPROPER("佩戴不规范"),
    AMBIENT_LIGHT("环境光线影响"),
    SITE_ORGANIZATION("现场组织问题"),
    OTHER("其他原因");

    private final String label;

    RetestReason(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
