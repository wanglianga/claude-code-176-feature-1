package com.county.vision.service;

import com.county.vision.cache.CacheService;
import com.county.vision.domain.entity.School;
import com.county.vision.domain.entity.Student;
import com.county.vision.domain.enums.ScreenFlag;
import com.county.vision.domain.enums.StudentStage;
import com.county.vision.domain.repository.*;
import com.county.vision.security.LoginUser;
import com.county.vision.security.SecurityUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 县教体/卫健部门监管看板：趋势、真实复诊率、干预执行、补助发放、异常闭环 */
@Service
public class StatsService {

    private final StudentRepository studentRepo;
    private final ScreenRecordRepository screenRepo;
    private final VisitRecordRepository visitRepo;
    private final GlassesRecordRepository glassesRepo;
    private final SubsidyApplicationRepository subsidyRepo;
    private final IssueTrackerRepository issueRepo;
    private final SchoolRepository schoolRepo;
    private final CacheService cache;
    private final RetestRecordRepository retestRepo;
    private final ReferralTaskRepository referralRepo;

    @PersistenceContext
    private EntityManager em;

    public StatsService(StudentRepository studentRepo, ScreenRecordRepository screenRepo,
                        VisitRecordRepository visitRepo, GlassesRecordRepository glassesRepo,
                        SubsidyApplicationRepository subsidyRepo, IssueTrackerRepository issueRepo,
                        SchoolRepository schoolRepo, CacheService cache,
                        RetestRecordRepository retestRepo, ReferralTaskRepository referralRepo) {
        this.studentRepo = studentRepo;
        this.screenRepo = screenRepo;
        this.visitRepo = visitRepo;
        this.glassesRepo = glassesRepo;
        this.subsidyRepo = subsidyRepo;
        this.issueRepo = issueRepo;
        this.schoolRepo = schoolRepo;
        this.cache = cache;
        this.retestRepo = retestRepo;
        this.referralRepo = referralRepo;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> overview(Long schoolId, Long regionId) {
        LoginUser me = SecurityUtils.current();
        if (me.getSchoolId() != null) schoolId = me.getSchoolId();

        String cacheKey = "stats:overview:" + schoolId + ":" + regionId;
        Map<String, Object> cached = cache.get(cacheKey, Map.class);
        if (cached != null) return cached;

        List<Student> students = studentRepo.search(schoolId, null, regionId, null, null, false);
        long total = students.size();
        long screened = students.stream().filter(s -> s.getStage() != StudentStage.PENDING_SCREEN
                && s.getStage() != StudentStage.LOST_FOLLOW).count();
        long abnormalStudents = students.stream().filter(this::isAbnormal).count();
        long visitCompleted = students.stream().filter(this::hasVisited).count();
        long trueMyopia = visitRepo.countByTrueMyopiaTrue();
        long managing = students.stream().filter(s -> s.getStage() == StudentStage.MANAGING
                || s.getStage() == StudentStage.CLOSED_LOOP).count();
        long deferred = students.stream().filter(s -> s.getStage() == StudentStage.DEFERRED).count();
        long lostFollow = students.stream().filter(s -> s.getStage() == StudentStage.LOST_FOLLOW).count();
        long reviewDue = students.stream().filter(s -> s.getStage() == StudentStage.REVIEW_DUE).count();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalStudents", total);
        m.put("screenedCount", screened);
        m.put("abnormalCount", abnormalStudents);
        m.put("trueMyopiaCount", trueMyopia);
        m.put("visitedCount", visitCompleted);
        m.put("managingCount", managing);
        m.put("deferredCount", deferred);
        m.put("lostFollowCount", lostFollow);
        m.put("reviewDueCount", reviewDue);
        m.put("externalGlasses", glassesRepo.countByPartnerOpticianFalse());
        m.put("discomfortOpen", glassesRepo.countByDiscomfortTrueAndDiscomfortResolvedFalse());
        m.put("openIssues", issueRepo.countByStatus("OPEN"));
        m.put("resolvedIssues", issueRepo.countByStatus("RESOLVED"));
        m.put("screenRate", rate(screened, total));
        m.put("abnormalRate", rate(abnormalStudents, total));
        // 真实复诊率 = 已完成复诊人数 / 筛查异常人数（卫健核对：避免异常学生无人跟进）
        m.put("realVisitRate", rate(visitCompleted, abnormalStudents));
        m.put("closedLoopRate", rate(issueRepo.countByStatus("RESOLVED"),
                issueRepo.countByStatus("OPEN") + issueRepo.countByStatus("RESOLVED")));
        m.put("subsidyPaid", subsidyRepo.countByStatus(
                com.county.vision.domain.enums.SubsidyStatus.PAID));
        m.put("subsidyDispute", subsidyRepo.countByStatus(
                com.county.vision.domain.enums.SubsidyStatus.DISPUTE));

        // 二次复测与转诊（家长复诊提醒）指标
        m.put("retestPending", retestRepo.findByStatusOrderByIdDesc(
                com.county.vision.domain.entity.RetestRecord.STATUS_PENDING).size());
        m.put("retestAbnormalConfirmed", retestRepo.findByStatusOrderByIdDesc(
                com.county.vision.domain.entity.RetestRecord.STATUS_ABNORMAL_CONFIRMED).size());
        m.put("retestNormalClosed", retestRepo.findByStatusOrderByIdDesc(
                com.county.vision.domain.entity.RetestRecord.STATUS_NORMAL_CLOSED).size());
        m.put("referralOpen", referralRepo.countByStatus(
                com.county.vision.domain.entity.ReferralTask.STATUS_PENDING)
                + referralRepo.countByStatus(com.county.vision.domain.entity.ReferralTask.STATUS_CONFIRMED));
        m.put("referralVisited", referralRepo.countByStatus(
                com.county.vision.domain.entity.ReferralTask.STATUS_VISITED));
        m.put("referralClosed", referralRepo.countByStatus(
                com.county.vision.domain.entity.ReferralTask.STATUS_CLOSED));

        cache.put(cacheKey, m, Duration.ofSeconds(30));
        return m;
    }

    private boolean isAbnormal(Student s) {
        // 经筛查/复核确认进入异常管理链路（请假失访与筛查正常不计入异常分母）
        return s.getStage() != StudentStage.PENDING_SCREEN
                && s.getStage() != StudentStage.SCREENED
                && s.getStage() != StudentStage.LOST_FOLLOW;
    }

    private boolean hasVisited(Student s) {
        return switch (s.getStage()) {
            case VISITED, GLASSES_SUBSIDY, MANAGING, REVIEW_DUE, CLOSED_LOOP -> true;
            default -> false;
        };
    }

    private double rate(long part, long total) {
        if (total == 0) return 0.0;
        return Math.round(part * 10000.0 / total) / 100.0;
    }

    /** 按年级观察近视发展趋势 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> trendByGrade(Long schoolId, Long regionId) {
        LoginUser me = SecurityUtils.current();
        if (me.getSchoolId() != null) schoolId = me.getSchoolId();
        List<Student> students = studentRepo.search(schoolId, null, regionId, null, null, false);

        Map<String, long[]> byGrade = new LinkedHashMap<>();
        for (Student s : students) {
            String grade = s.getGradeClass().getGrade();
            long[] arr = byGrade.computeIfAbsent(grade, k -> new long[4]); // total, abnormal, visited, trueMyopia-ish
            arr[0]++;
            if (isAbnormal(s)) arr[1]++;
            if (hasVisited(s)) arr[2]++;
        }
        List<Map<String, Object>> out = new ArrayList<>();
        byGrade.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("grade", e.getKey());
            row.put("total", e.getValue()[0]);
            row.put("abnormal", e.getValue()[1]);
            row.put("visited", e.getValue()[2]);
            row.put("abnormalRate", rate(e.getValue()[1], e.getValue()[0]));
            row.put("visitRate", rate(e.getValue()[2], e.getValue()[1]));
            out.add(row);
        });
        return out;
    }

    /** 按学校对比（片区/全县排名） */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> bySchool(Long regionId) {
        List<School> schools = regionId == null ? schoolRepo.findAllByOrderByIdAsc()
                : schoolRepo.findByRegionId(regionId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (School sch : schools) {
            List<Student> students = studentRepo.findBySchoolIdOrderByIdAsc(sch.getId());
            long total = students.size();
            long abnormal = students.stream().filter(this::isAbnormal).count();
            long visited = students.stream().filter(this::hasVisited).count();
            long interventions = ((Number) em.createQuery(
                            "select count(i) from Intervention i where i.student.school.id = :sid")
                    .setParameter("sid", sch.getId()).getSingleResult()).longValue();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("schoolId", sch.getId());
            row.put("schoolName", sch.getName());
            row.put("regionName", sch.getRegion() != null ? sch.getRegion().getName() : "—");
            row.put("total", total);
            row.put("abnormal", abnormal);
            row.put("visited", visited);
            row.put("abnormalRate", rate(abnormal, total));
            row.put("realVisitRate", rate(visited, abnormal));
            row.put("interventions", interventions);
            out.add(row);
        }
        return out;
    }

    /** 按片区观察 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> byRegion() {
        List<School> schools = schoolRepo.findAllByOrderByIdAsc();
        Map<String, long[]> agg = new LinkedHashMap<>();
        for (School sch : schools) {
            String region = sch.getRegion() != null ? sch.getRegion().getName() : "未划分";
            long[] arr = agg.computeIfAbsent(region, k -> new long[3]);
            List<Student> students = studentRepo.findBySchoolIdOrderByIdAsc(sch.getId());
            arr[0] += students.size();
            arr[1] += students.stream().filter(this::isAbnormal).count();
            arr[2] += students.stream().filter(this::hasVisited).count();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        agg.forEach((region, arr) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("region", region);
            row.put("total", arr[0]);
            row.put("abnormal", arr[1]);
            row.put("visited", arr[2]);
            row.put("abnormalRate", rate(arr[1], arr[0]));
            row.put("realVisitRate", rate(arr[2], arr[1]));
            out.add(row);
        });
        return out;
    }

    /** 异常事项分布（8 类异常场景的待处理/已闭环） */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> issueDistribution() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ScreenFlag f : ScreenFlag.values()) {
            long open = ((Number) em.createQuery(
                            "select count(t) from IssueTracker t where t.issueType = :t and t.status = 'OPEN'")
                    .setParameter("t", f.name()).getSingleResult()).longValue();
            long resolved = ((Number) em.createQuery(
                            "select count(t) from IssueTracker t where t.issueType = :t and t.status = 'RESOLVED'")
                    .setParameter("t", f.name()).getSingleResult()).longValue();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", f.name());
            row.put("label", f.getLabel());
            row.put("open", open);
            row.put("resolved", resolved);
            out.add(row);
        }
        return out;
    }

    /** 阶段漏斗 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> stageFunnel(Long schoolId, Long regionId) {
        LoginUser me = SecurityUtils.current();
        if (me.getSchoolId() != null) schoolId = me.getSchoolId();
        List<Student> students = studentRepo.search(schoolId, null, regionId, null, null, false);
        Map<StudentStage, Long> counts = new LinkedHashMap<>();
        for (StudentStage st : StudentStage.values()) counts.put(st, 0L);
        students.forEach(s -> counts.merge(s.getStage(), 1L, Long::sum));
        List<Map<String, Object>> out = new ArrayList<>();
        counts.forEach((st, c) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("stage", st.name());
            row.put("label", st.getLabel());
            row.put("count", c);
            out.add(row);
        });
        return out;
    }

    /**
     * 二次复测比例分析（按班级 / 设备 / 场地）。
     * 用于区分学生个体问题与设备、光线、现场组织问题：
     * 复测发起比例越高说明现场对结果越谨慎；复测后恢复正常占比高 → 偏向设备遮挡/佩戴/光线/组织问题；
     * 复测后仍异常占比高 → 偏向学生个体视力问题。同一设备/场地的首筛异常率持续偏高也在此暴露。
     */
    @Transactional(readOnly = true)
    public Map<String, Object> retestStats(Long schoolId) {
        LoginUser me = SecurityUtils.current();
        if (me.getSchoolId() != null) schoolId = me.getSchoolId();

        List<com.county.vision.domain.entity.ScreenRecord> all = schoolId == null
                ? screenRepo.findByRound("FIRST")
                : screenRepo.findBySchoolAndRound(schoolId, "FIRST");
        List<com.county.vision.domain.entity.ScreenRecord> firsts = all.stream()
                .filter(r -> !Boolean.TRUE.equals(r.getAbsent())).toList();
        List<com.county.vision.domain.entity.ScreenRecord> retests = (schoolId == null
                ? screenRepo.findByRound("RETEST")
                : screenRepo.findBySchoolAndRound(schoolId, "RETEST"));
        // 复测筛查记录 -> 对应的首次（原始）记录：把复测结论归因到触发复测的设备/场地，
        // 这样"换设备后复测正常"才能正确暴露原设备/原场地问题。
        Map<Long, com.county.vision.domain.entity.ScreenRecord> retestToOriginal = new LinkedHashMap<>();
        for (com.county.vision.domain.entity.RetestRecord rr : retestRepo.findAll()) {
            if (rr.getRetestScreen() != null) {
                retestToOriginal.put(rr.getRetestScreen().getId(), rr.getOriginalScreen());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        // 按班级：复测归因到学生班级（首复测学生相同）
        result.put("byClass", aggregate(firsts, retests, r -> r,
                r -> {
                    var gc = r.getStudent().getGradeClass();
                    return gc.getGrade() + gc.getClassName() + "@" + r.getStudent().getSchool().getName();
                }, r -> {
                    var gc = r.getStudent().getGradeClass();
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("schoolName", r.getStudent().getSchool().getName());
                    m.put("grade", gc.getGrade());
                    m.put("className", gc.getClassName());
                    return m;
                }));
        // 按设备：复测结论归因到首次筛查所用设备
        result.put("byDevice", aggregate(firsts, retests,
                r -> retestToOriginal.getOrDefault(r.getId(), r),
                r -> blankKey(r.getDeviceCalibNo(), "未登记设备"),
                r -> Map.of("deviceCalibNo", blankKey(r.getDeviceCalibNo(), "未登记设备"))));
        // 按场地：复测结论归因到首次筛查所在场地
        result.put("byVenue", aggregate(firsts, retests,
                r -> retestToOriginal.getOrDefault(r.getId(), r),
                r -> blankKey(r.getVenue(), "未登记场地"),
                r -> Map.of("venue", blankKey(r.getVenue(), "未登记场地"))));

        long firstAbnormal = firsts.stream().filter(r -> Boolean.TRUE.equals(r.getAbnormal())).count();
        long retestTotal = retests.size();
        long retestNormal = retests.stream().filter(r -> !Boolean.TRUE.equals(r.getAbnormal())).count();
        long retestAbnormal = retests.stream().filter(r -> Boolean.TRUE.equals(r.getAbnormal())).count();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("firstAbnormal", firstAbnormal);
        summary.put("retestTotal", retestTotal);
        summary.put("retestNormal", retestNormal);
        summary.put("retestAbnormal", retestAbnormal);
        // 复测发起比例 = 已复测人次 / 首筛异常人次
        summary.put("retestRate", rate(retestTotal, firstAbnormal));
        // 复测恢复正常占比 → 设备/光线/现场组织问题信号
        summary.put("normalRate", rate(retestNormal, retestTotal));
        // 复测仍异常占比 → 学生个体问题信号
        summary.put("confirmRate", rate(retestAbnormal, retestTotal));
        result.put("summary", summary);
        return result;
    }

    private interface KeyMeta {
        Map<String, Object> meta(com.county.vision.domain.entity.ScreenRecord r);
    }

    /**
     * 聚合首筛与复测。复测记录先经 contextFn 映射到"归因记录"（按设备/场地时映射回首次记录，
     * 按班级时映射为自身），再用 keyFn 归桶，从而把"换设备复测正常"归因到触发复测的设备/场地。
     */
    private List<Map<String, Object>> aggregate(
            List<com.county.vision.domain.entity.ScreenRecord> firsts,
            List<com.county.vision.domain.entity.ScreenRecord> retests,
            java.util.function.Function<com.county.vision.domain.entity.ScreenRecord,
                    com.county.vision.domain.entity.ScreenRecord> contextFn,
            java.util.function.Function<com.county.vision.domain.entity.ScreenRecord, String> keyFn,
            KeyMeta metaFn) {
        // 桶：[首筛总数, 首筛异常, 复测总数, 复测正常, 复测仍异常]
        Map<String, long[]> buckets = new LinkedHashMap<>();
        Map<String, Map<String, Object>> metas = new LinkedHashMap<>();

        for (var r : firsts) {
            String k = keyFn.apply(r);
            long[] a = buckets.computeIfAbsent(k, x -> new long[5]);
            a[0]++;
            if (Boolean.TRUE.equals(r.getAbnormal())) a[1]++;
            metas.putIfAbsent(k, metaFn.meta(r));
        }
        for (var r : retests) {
            var ctx = contextFn.apply(r);
            if (ctx == null) continue;
            String k = keyFn.apply(ctx);
            long[] a = buckets.computeIfAbsent(k, x -> new long[5]);
            a[2]++;
            if (Boolean.TRUE.equals(r.getAbnormal())) a[4]++;
            else a[3]++;
            metas.putIfAbsent(k, metaFn.meta(ctx));
        }

        List<Map<String, Object>> out = new ArrayList<>();
        buckets.forEach((k, a) -> {
            Map<String, Object> row = new LinkedHashMap<>(metas.get(k));
            row.put("firstTotal", a[0]);
            row.put("firstAbnormal", a[1]);
            row.put("firstAbnormalRate", rate(a[1], a[0]));
            row.put("retestTotal", a[2]);
            row.put("retestNormal", a[3]);
            row.put("retestAbnormal", a[4]);
            // 复测发起比例 = 复测人次 / 首筛异常人次
            row.put("retestRate", rate(a[2], a[1]));
            // 复测恢复正常占比（设备/光线/现场组织信号）
            row.put("normalRate", rate(a[3], a[2]));
            // 复测仍异常占比（学生个体信号）
            row.put("confirmRate", rate(a[4], a[2]));
            // 风险提示：首筛异常率高且复测多恢复正常 → 优先排查设备/光线/现场组织
            String signal;
            if (a[2] == 0) {
                signal = a[1] >= 3 ? "连续异常待复测排查" : "数据积累中";
            } else if (rate(a[3], a[2]) >= 50) {
                signal = "偏向设备/光线/现场组织问题";
            } else if (rate(a[4], a[2]) >= 50) {
                signal = "偏向学生个体问题";
            } else {
                signal = "个体与现场因素并存";
            }
            row.put("signal", signal);
            out.add(row);
        });
        // 连续异常（首筛异常数）多的排前
        out.sort((x, y) -> Long.compare((long) y.get("firstAbnormal"), (long) x.get("firstAbnormal")));
        return out;
    }

    private String blankKey(String v, String fallback) {
        return (v == null || v.isBlank()) ? fallback : v;
    }
}
