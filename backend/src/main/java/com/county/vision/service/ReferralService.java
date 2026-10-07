package com.county.vision.service;

import com.county.vision.domain.entity.ReferralTask;
import com.county.vision.domain.entity.RetestRecord;
import com.county.vision.domain.entity.Student;
import com.county.vision.domain.enums.EventType;
import com.county.vision.domain.repository.ReferralTaskRepository;
import com.county.vision.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 转诊任务（家长复诊提醒）的统一生命周期管理。
 * 核心约束：
 * 1. 同一学生同一异常周期只保留一个未结束任务，重复发起一律复用，避免重复创建复诊任务；
 * 2. 任务只关闭、不物理删除，已发出的家长通知（渠道/时间）永久留痕；
 * 3. 只有复核确认或二次复测仍异常才生成，复测恢复正常不生成（并关闭未发出的挂起任务）。
 */
@Service
public class ReferralService {

    /** 未结束：待家长告知/确认、已确认待复诊 */
    public static final List<String> OPEN_STATUSES =
            List.of(ReferralTask.STATUS_PENDING, ReferralTask.STATUS_CONFIRMED);

    private final ReferralTaskRepository referralRepo;
    private final TimelineService timeline;

    public ReferralService(ReferralTaskRepository referralRepo, TimelineService timeline) {
        this.referralRepo = referralRepo;
        this.timeline = timeline;
    }

    /** 学校健康老师复核确认异常：生成（或复用）家长复诊提醒任务 */
    @Transactional
    public ReferralTask ensureFromReview(Student s) {
        return referralRepo.findFirstByStudentIdAndStatusInOrderByIdDesc(s.getId(), OPEN_STATUSES)
                .orElseGet(() -> {
                    ReferralTask t = create(s, ReferralTask.SOURCE_REVIEW, null);
                    timeline.event(s, EventType.SCHOOL_REVIEW,
                            "复核确认异常，已生成家长复诊提醒（转诊任务 #" + t.getId() + "），等待告知家长");
                    return t;
                });
    }

    /** 二次复测仍异常：生成家长复诊提醒；已有未结束任务则复用，杜绝重复任务 */
    @Transactional
    public ReferralTask ensureFromRetest(Student s, RetestRecord retest) {
        return referralRepo.findFirstByStudentIdAndStatusInOrderByIdDesc(s.getId(), OPEN_STATUSES)
                .orElseGet(() -> {
                    ReferralTask t = create(s, ReferralTask.SOURCE_RETEST, retest);
                    timeline.event(s, EventType.RETEST,
                            "二次复测仍异常，已生成家长复诊提醒（转诊任务 #" + t.getId() + "）");
                    return t;
                });
    }

    private ReferralTask create(Student s, String source, RetestRecord retest) {
        ReferralTask t = new ReferralTask();
        t.setStudent(s);
        t.setRetestRecord(retest);
        t.setSource(source);
        t.setStatus(ReferralTask.STATUS_PENDING);
        t.setNotified(false);
        t.setCreatedBy(SecurityUtils.actorLabel());
        return referralRepo.save(t);
    }

    /** 记录"已向家长发出通知"，通知一旦发出即在任务上永久留痕 */
    @Transactional
    public void markNotified(Student s, String channel) {
        referralRepo.findFirstByStudentIdAndStatusInOrderByIdDesc(s.getId(), OPEN_STATUSES)
                .ifPresent(t -> {
                    boolean first = !Boolean.TRUE.equals(t.getNotified());
                    t.setNotified(true);
                    t.setNotifyChannel(channel);
                    t.setNotifyTime(LocalDateTime.now());
                    referralRepo.save(t);
                    if (first) {
                        timeline.event(s, EventType.NOTIFY,
                                "家长复诊提醒（转诊任务 #" + t.getId() + "）已通过" + channel + "发出，通知记录保留不可删除");
                    }
                });
    }

    /** 家长确认并选择复诊机构 */
    @Transactional
    public void markConfirmed(Student s, String targetOrg) {
        referralRepo.findFirstByStudentIdAndStatusInOrderByIdDesc(s.getId(), OPEN_STATUSES)
                .ifPresent(t -> {
                    t.setStatus(ReferralTask.STATUS_CONFIRMED);
                    t.setTargetOrg(targetOrg);
                    referralRepo.save(t);
                });
    }

    /** 医院复诊完成回写 */
    @Transactional
    public void markVisited(Student s) {
        referralRepo.findFirstByStudentIdAndStatusInOrderByIdDesc(s.getId(), OPEN_STATUSES)
                .ifPresent(t -> {
                    t.setStatus(ReferralTask.STATUS_VISITED);
                    referralRepo.save(t);
                    timeline.event(s, EventType.VISIT,
                            "转诊任务 #" + t.getId() + " 已完成医院复诊，任务结案（记录保留）");
                });
    }

    /**
     * 二次复测恢复正常：关闭本次转诊任务。
     * 任务记录与已发通知保留（状态置 CLOSED 并写明关闭原因），不做物理删除；
     * 关闭结论同时由调用方写入家长可见的健康时间线。
     * @return 是否存在并关闭了任务
     */
    @Transactional
    public boolean closeOpen(Student s, String closeNote) {
        return referralRepo.findFirstByStudentIdAndStatusInOrderByIdDesc(s.getId(), OPEN_STATUSES)
                .map(t -> {
                    boolean notified = Boolean.TRUE.equals(t.getNotified());
                    t.setStatus(ReferralTask.STATUS_CLOSED);
                    t.setCloseNote(closeNote);
                    t.setClosedBy(SecurityUtils.actorLabel());
                    t.setClosedAt(LocalDateTime.now());
                    referralRepo.save(t);
                    timeline.event(s, EventType.RETEST,
                            "转诊任务 #" + t.getId() + " 已关闭（记录"
                                    + (notified ? "及已发出的家长通知" : "") + "均保留留痕，不做删除）：" + closeNote);
                    return true;
                }).orElse(false);
    }
}
