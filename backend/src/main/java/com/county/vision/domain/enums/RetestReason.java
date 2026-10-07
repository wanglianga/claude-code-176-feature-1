package com.county.vision.domain.enums;

/**
 * 发起二次确认复测的原因：
 * 首次筛查异常可能来自设备遮挡、佩戴不规范或环境光线等现场因素，而非学生视力本身。
 */
public enum RetestReason {
    DEVICE_BLOCK("设备遮挡/读数异常"),
    WEAR_IRREGULAR("佩戴不规范"),
    ENV_LIGHT("环境光线影响"),
    STATE_FLUCTUATION("学生状态波动/配合度差"),
    OTHER("其他现场原因");

    private final String label;

    RetestReason(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
