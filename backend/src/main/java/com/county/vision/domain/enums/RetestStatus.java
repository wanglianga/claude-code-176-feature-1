package com.county.vision.domain.enums;

/**
 * 复测任务（二次确认）状态
 */
public enum RetestStatus {
    PENDING("待复测"),
    CONFIRMED_ABNORMAL("复测仍异常"),
    CLEARED_NORMAL("复测恢复正常");

    private final String label;

    RetestStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
