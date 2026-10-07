package com.county.vision.service;

/**
 * 筛查结果异常判定（首次筛查与二次复测共用同一标准，保证两次数据可比）。
 * 任一眼裸眼视力低于 4.9（五分记录）/1.0（小数记录），或等效球镜 ≤ -0.50D 视为异常。
 */
public abstract class ScreenJudge {

    public static boolean judgeAbnormal(String visionL, String visionR, String sphereL, String sphereR) {
        return belowVision(visionL) || belowVision(visionR)
                || myopicSphere(sphereL) || myopicSphere(sphereR);
    }

    public static boolean belowVision(String v) {
        if (v == null || v.isBlank()) return false;
        try {
            double d = Double.parseDouble(v.trim());
            // 兼容 5 分记录（4.9 以下）和小数记录（1.0 以下）
            return (d > 2 && d < 4.9) || (d <= 2 && d < 1.0);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static boolean myopicSphere(String sph) {
        if (sph == null || sph.isBlank()) return false;
        try {
            return Double.parseDouble(sph.replace("D", "").trim()) <= -0.50;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static String buildAbnormalReason(String visionL, String visionR, String sphereL, String sphereR) {
        StringBuilder sb = new StringBuilder();
        if (belowVision(visionL) || belowVision(visionR)) {
            sb.append("裸眼视力低于4.9；");
        }
        if (myopicSphere(sphereL) || myopicSphere(sphereR)) {
            sb.append("球镜度数提示近视；");
        }
        return sb.toString();
    }
}
