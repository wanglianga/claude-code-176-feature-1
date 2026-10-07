package com.county.vision.service;

import com.county.vision.common.BusinessException;
import com.county.vision.domain.entity.ReferralTask;
import com.county.vision.domain.entity.RetestRecord;
import com.county.vision.domain.entity.ScreenRecord;
import com.county.vision.domain.entity.Student;
import com.county.vision.domain.enums.EventType;
import com.county.vision.domain.enums.RetestReason;
import com.county.vision.domain.enums.StudentStage;
import com.county.vision.domain.repository.IssueTrackerRepository;
import com.county.vision.domain.repository.ReferralTaskRepository;
import com.county.vision.domain.repository.RetestRecordRepository;
import com.county.vision.domain.repository.ScreenRecordRepository;
import com.county.vision.domain.repository.StudentRepository;
import com.county.vision.dto.RecordDtos;
import com.county.vision.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 筛查异常二次确认（复测）。
 *
 * 流程：健康老师在原异常记录上发起复测（填复测原因：设备遮挡/佩戴不规范/环境光线等）→
 * 首次结果暂不推送家长 → 现场用（可换）设备复测并录入第二次数据 →
 * 复测仍异常：维持判定，幂等生成家长复诊提醒（转诊任务），两次数据、设备编号、操作人全程保留；
 * 复测恢复正常：记录异常原因，关闭本次转诊任务（不删除、已发通知留痕），学生回到已筛查正常。
 */
@Service
public class RetestService {

    private final RetestRecordRepository retestRepo;
    private final ScreenRecordRepository screenRepo;
    private final StudentRepository studentRepo;
    private final ReferralTaskRepository referralRepo;
    private final IssueTrackerRepository issueRepo;
    private final ReferralService referralService;
    private final TimelineService timeline;

    public RetestService(RetestRecordRepository retestRepo, ScreenRecordRepository screenRepo,
                         StudentRepository studentRepo, ReferralTaskRepository referralRepo,
                         IssueTrackerRepository issueRepo, ReferralService referralService,
                         TimelineService timeline) {
        this.retestRepo = retestRepo;
        this.screenRepo = screenRepo;
        this.studentRepo = studentRepo;
        this.referralRepo = referralRepo;
        this.issueRepo = issueRepo;
        this.referralService = referralService;
        this.timeline = timeline;
    }

    /* ---------------- 发起复测 ---------------- */

    @Transactional
    public RetestRecord initiate(RecordDtos.RetestInitiateRequest req) {
        ScreenRecord original = resolveOriginal(req);
        Student s = original.getStudent();

        if (Boolean.TRUE.equals(original.getAbsent())) {
            throw new BusinessException("请假未检记录不能发起复测，请安排补检");
        }
        if (!Boolean.TRUE.equals(original.getAbnormal())) {
            throw new BusinessException("只能对筛查异常的记录发起二次复测");
        }
        // 同一异常周期不得重复发起
        retestRepo.findFirstByStudentIdAndStatusOrderByIdDesc(s.getId(), RetestRecord.STATUS_PENDING)
                .ifPresent(r -> {
                    throw new BusinessException("该学生已有待录入结果的复测（复测记录 #" + r.getId() + "），请勿重复发起");
                });
        // 家长已确认选择复诊机构后，不再走现场二次复测（走复诊差异登记）
        referralRepo.findFirstByStudentIdAndStatusInOrderByIdDesc(s.getId(),
                        List.of(ReferralTask.STATUS_CONFIRMED, ReferralTask.STATUS_VISITED))
                .ifPresent(t -> {
                    throw new BusinessException("家长已确认/完成复诊（转诊任务 #" + t.getId()
                            + "），不能再发起现场复测，如结果有差异请在复诊环节登记");
                });

        RetestReason reason;
        try {
            reason = RetestReason.valueOf(req.getReason());
        } catch (Exception e) {
            throw new BusinessException("复测原因不合法，应为 DEVICE_BLOCK/WEAR_IRREGULAR/ENV_LIGHT/STATE_FLUCTUATION/OTHER");
        }

        RetestRecord r = new RetestRecord();
        r.setStudent(s);
        r.setOriginalScreen(original);
        r.setReason(reason);
        r.setReasonDetail(req.getReasonDetail());
        r.setStatus(RetestRecord.STATUS_PENDING);
        r.setInitiatedBy(SecurityUtils.actorLabel());
        r.setInitiatedAt(LocalDateTime.now());
        RetestRecord saved = retestRepo.save(r);

        timeline.event(s, EventType.RETEST, String.format(
                "健康老师在原筛查记录 #%s 上发起二次确认复测（复测 #%s），原因：%s%s；"
                        + "首次异常结果暂不推送家长，待复测后再决定是否生成复诊提醒",
                original.getId(), saved.getId(), reason.getLabel(),
                req.getReasonDetail() == null || req.getReasonDetail().isBlank()
                        ? "" : "（" + req.getReasonDetail() + "）"));
        timeline.changeStage(s, StudentStage.ABNORMAL_REVIEW);
        return saved;
    }

    private ScreenRecord resolveOriginal(RecordDtos.RetestInitiateRequest req) {
        if (req.getOriginalScreenId() != null) {
            return screenRepo.findById(req.getOriginalScreenId())
                    .orElseThrow(() -> new BusinessException("原筛查记录不存在"));
        }
        if (req.getStudentId() == null) {
            throw new BusinessException("请指定原筛查记录或学生");
        }
        Student s = studentRepo.findById(req.getStudentId())
                .orElseThrow(() -> new BusinessException("学生不存在"));
        return screenRepo.findFirstByStudentIdAndScreenRoundOrderByScreenDateDescIdDesc(s.getId(), "FIRST")
                .orElseThrow(() -> new BusinessException("该学生暂无可发起复测的首次筛查记录"));
    }

    /* ---------------- 录入复测结果与结论 ---------------- */

    @Transactional
    public RetestRecord submit(RecordDtos.RetestSubmitRequest req) {
        RetestRecord r = retestRepo.findById(req.getRetestId())
                .orElseThrow(() -> new BusinessException("复测记录不存在"));
        if (!RetestRecord.STATUS_PENDING.equals(r.getStatus())) {
            throw new BusinessException("该复测已录入结论，不能重复提交");
        }
        Student s = r.getStudent();
        ScreenRecord first = r.getOriginalScreen();

        ScreenRecord second = new ScreenRecord();
        second.setStudent(s);
        second.setScreenDate(req.getRetestDate() == null ? LocalDate.now() : req.getRetestDate());
        second.setOrgName(orDefault(req.getOrgName(), first.getOrgName()));
        second.setOperatorName(orDefault(req.getOperatorName(), first.getOperatorName()));
        second.setDeviceCalibNo(orDefault(req.getDeviceCalibNo(), first.getDeviceCalibNo()));
        second.setVenue(orDefault(req.getVenue(), first.getVenue()));
        second.setNakedVisionLeft(req.getNakedVisionLeft());
        second.setNakedVisionRight(req.getNakedVisionRight());
        second.setSphereLeft(req.getSphereLeft());
        second.setSphereRight(req.getSphereRight());
        second.setCylinderLeft(req.getCylinderLeft());
        second.setCylinderRight(req.getCylinderRight());
        second.setWearingGlasses(Boolean.TRUE.equals(req.getWearingGlasses()));
        second.setHabitSummary(req.getHabitSummary());
        second.setRetestNeeded(false);
        second.setAbsent(false);
        second.setScreenRound("RETEST");
        boolean stillAbnormal = ScreenJudge.judgeAbnormal(
                req.getNakedVisionLeft(), req.getNakedVisionRight(),
                req.getSphereLeft(), req.getSphereRight());
        second.setAbnormal(stillAbnormal);
        if (stillAbnormal) {
            second.setAbnormalReason(ScreenJudge.buildAbnormalReason(
                    req.getNakedVisionLeft(), req.getNakedVisionRight(),
                    req.getSphereLeft(), req.getSphereRight()));
        }
        ScreenRecord secondSaved = screenRepo.save(second);

        r.setRetestScreen(secondSaved);
        r.setRetestOperatorName(secondSaved.getOperatorName());
        r.setRetestDeviceCalibNo(secondSaved.getDeviceCalibNo());
        r.setRetestOrgName(secondSaved.getOrgName());
        r.setConcludedBy(SecurityUtils.actorLabel());
        r.setConcludedAt(LocalDateTime.now());
        r.setConclusionNote(req.getConclusionNote());

        // 第二次测量数据同样进入时间线（家长可见档案的一部分）
        timeline.event(s, EventType.SCREEN, String.format(
                "二次复测数据：裸眼视力 左%s/右%s，球镜 左%s/右%s，散光 左%s/右%s，戴镜=%s，"
                        + "设备校准编号=%s，场地=%s，操作人=%s",
                nz(req.getNakedVisionLeft()), nz(req.getNakedVisionRight()),
                nz(req.getSphereLeft()), nz(req.getSphereRight()),
                nz(req.getCylinderLeft()), nz(req.getCylinderRight()),
                secondSaved.getWearingGlasses() ? "是" : "否",
                nz(secondSaved.getDeviceCalibNo()), nz(secondSaved.getVenue()),
                nz(secondSaved.getOperatorName())));

        if (stillAbnormal) {
            confirmAbnormal(r, secondSaved, s, req.getConclusionNote());
        } else {
            closeAsNormal(r, secondSaved, s, req.getConclusionNote());
        }
        closeRetestIssue(s);
        return retestRepo.save(r);
    }

    /** 复测仍异常：维持判定，幂等生成家长复诊提醒 */
    private void confirmAbnormal(RetestRecord r, ScreenRecord second, Student s, String note) {
        r.setStatus(RetestRecord.STATUS_ABNORMAL_CONFIRMED);
        r.setConclusion(RetestRecord.CONCLUSION_ABNORMAL);
        String conclusion = String.format(
                "二次复测仍异常，维持筛查异常判定（首次：左%s/右%s，复测：左%s/右%s；复测设备=%s，操作人=%s）。"
                        + "已生成家长复诊提醒，首次与复测两次数据均保留%s",
                nz(r.getOriginalScreen().getNakedVisionLeft()),
                nz(r.getOriginalScreen().getNakedVisionRight()),
                nz(second.getNakedVisionLeft()), nz(second.getNakedVisionRight()),
                nz(second.getDeviceCalibNo()), nz(second.getOperatorName()),
                note == null || note.isBlank() ? "" : "。说明：" + note);
        r.setConclusionNote(r.getConclusionNote() == null ? conclusion
                : r.getConclusionNote() + "｜" + conclusion);
        timeline.event(s, EventType.RETEST, "复测结论：" + conclusion + "（已同步家长可见档案）");

        ReferralTask t = referralService.ensureFromRetest(s, r);
        timeline.event(s, EventType.NOTIFY,
                "请家长告知环节向家长推送复诊提醒（转诊任务 #" + t.getId() + "，来源：二次复测）；"
                        + "在复测确认前未向家长推送第一次异常结果");
        timeline.changeStage(s, StudentStage.AWAIT_PARENT);
    }

    /** 复测恢复正常：留下异常原因，关闭本次转诊任务（不删除、通知留痕） */
    private void closeAsNormal(RetestRecord r, ScreenRecord second, Student s, String note) {
        r.setStatus(RetestRecord.STATUS_NORMAL_CLOSED);
        r.setConclusion(RetestRecord.CONCLUSION_NORMAL);
        String abnormalCause = "首次异常归因为现场因素【" + r.getReason().getLabel() + "】"
                + (r.getReasonDetail() == null || r.getReasonDetail().isBlank()
                        ? "" : "：" + r.getReasonDetail());
        String conclusion = String.format(
                "二次复测恢复正常（复测裸眼视力 左%s/右%s，球镜 左%s/右%s；复测设备=%s，操作人=%s）。%s；"
                        + "本次不向家长推送复诊，已关闭本次转诊任务，首次/复测两次数据保留，结论同步家长档案",
                nz(second.getNakedVisionLeft()), nz(second.getNakedVisionRight()),
                nz(second.getSphereLeft()), nz(second.getSphereRight()),
                nz(second.getDeviceCalibNo()), nz(second.getOperatorName()), abnormalCause);
        r.setConclusionNote(r.getConclusionNote() == null ? conclusion
                : r.getConclusionNote() + "｜" + conclusion);

        String closeNote = "二次复测恢复正常，" + abnormalCause
                + (note == null || note.isBlank() ? "" : "；" + note);
        boolean hadTask = referralService.closeOpen(s, closeNote);
        timeline.event(s, EventType.RETEST,
                "复测结论：" + conclusion + (hadTask ? "；转诊任务已关闭（记录与已发通知保留，不做删除）" : "")
                        + "（已同步家长可见档案）");
        timeline.changeStage(s, StudentStage.SCREENED);
    }

    /** 复测结案后，关闭筛查/复核阶段挂起的"需要复测"异常事项 */
    private void closeRetestIssue(Student s) {
        issueRepo.findFirstByStudentIdAndIssueTypeAndStatus(s.getId(), "RETEST_NEEDED", "OPEN")
                .ifPresent(it -> timeline.resolveIssue(it.getId(), "已完成二次确认复测并给出结论"));
    }

    private String orDefault(String v, String fallback) {
        return (v == null || v.isBlank()) ? fallback : v;
    }

    private String nz(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }
}
