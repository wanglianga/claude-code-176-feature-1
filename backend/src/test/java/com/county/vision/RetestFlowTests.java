package com.county.vision;

import com.county.vision.common.BusinessException;
import com.county.vision.domain.entity.*;
import com.county.vision.domain.enums.RetestStatus;
import com.county.vision.domain.enums.Role;
import com.county.vision.domain.enums.StudentStage;
import com.county.vision.domain.repository.*;
import com.county.vision.dto.RecordDtos;
import com.county.vision.security.LoginUser;
import com.county.vision.service.FlowService;
import com.county.vision.service.StatsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 筛查异常二次确认（复测）流程测试：
 * 发起复测 → 保留两次数据/设备/操作人/原因 → 复测仍异常生成家长复诊提醒（去重）、
 * 复测正常关闭转诊任务（已发通知标记取消不删除）→ 按设备统计复测比例。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RetestFlowTests {

    @Autowired
    FlowService flow;
    @Autowired
    StatsService stats;
    @Autowired
    StudentRepository studentRepo;
    @Autowired
    ScreenRecordRepository screenRepo;
    @Autowired
    ParentConfirmationRepository confirmRepo;
    @Autowired
    RetestTaskRepository retestRepo;
    @Autowired
    IssueTrackerRepository issueRepo;
    @Autowired
    SchoolRepository schoolRepo;
    @Autowired
    GradeClassRepository classRepo;
    @Autowired
    RegionRepository regionRepo;

    private GradeClass gc;
    private int seq = 0;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new LoginUser(1L, "doctor1", Role.SCHOOL_DOCTOR, null));
        Region r = new Region();
        r.setName("测试片区");
        r.setCode("T01");
        regionRepo.save(r);
        School school = new School();
        school.setName("测试小学");
        school.setRegion(r);
        schoolRepo.save(school);
        gc = new GradeClass();
        gc.setSchool(school);
        gc.setGrade("三年级");
        gc.setClassName("1班");
        classRepo.save(gc);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Student newStudent(String name) {
        Student s = new Student();
        s.setSchool(gc.getSchool());
        s.setGradeClass(gc);
        s.setStudentNo("T" + (++seq));
        s.setName(name);
        s.setGender("男");
        s.setParentName("测试家长");
        s.setParentPhone("13800000000");
        s.setScreenYear(2026);
        return studentRepo.save(s);
    }

    private RecordDtos.ScreenRequest screenReq(Long sid, String device, String vl, String vr, String sl, String sr) {
        RecordDtos.ScreenRequest r = new RecordDtos.ScreenRequest();
        r.setStudentId(sid);
        r.setScreenDate(LocalDate.of(2026, 9, 15));
        r.setOrgName("测试视光中心");
        r.setOperatorName("测试筛查员");
        r.setDeviceCalibNo(device);
        r.setNakedVisionLeft(vl);
        r.setNakedVisionRight(vr);
        r.setSphereLeft(sl);
        r.setSphereRight(sr);
        r.setWearingGlasses(false);
        r.setAbsent(false);
        return r;
    }

    private RecordDtos.ScreenRequest abnormalScreen(Long sid, String device) {
        return screenReq(sid, device, "4.5", "4.6", "-1.50", "-1.25");
    }

    private RecordDtos.RetestInitiateRequest init(Long sid, String reason) {
        RecordDtos.RetestInitiateRequest r = new RecordDtos.RetestInitiateRequest();
        r.setStudentId(sid);
        r.setReason(reason);
        r.setReasonDetail("测试复测原因");
        return r;
    }

    private RecordDtos.RetestCompleteRequest complete(Long taskId, boolean abnormal, String conclusion) {
        RecordDtos.RetestCompleteRequest r = new RecordDtos.RetestCompleteRequest();
        r.setTaskId(taskId);
        r.setScreenDate(LocalDate.of(2026, 9, 18));
        r.setOrgName("测试视光中心");
        r.setOperatorName("复测操作员");
        r.setDeviceCalibNo("DEV-CALIBRATED");
        if (abnormal) {
            r.setNakedVisionLeft("4.5");
            r.setNakedVisionRight("4.6");
            r.setSphereLeft("-1.50");
            r.setSphereRight("-1.25");
        } else {
            r.setNakedVisionLeft("5.0");
            r.setNakedVisionRight("5.0");
            r.setSphereLeft("0.00");
            r.setSphereRight("0.00");
        }
        r.setConclusion(conclusion);
        return r;
    }

    private void notifyParent(Long sid) {
        RecordDtos.NotifyRequest n = new RecordDtos.NotifyRequest();
        n.setStudentId(sid);
        n.setChannel("短信");
        n.setUnreachable(false);
        flow.notifyParent(n);
    }

    @Test
    void initiateCreatesPendingTaskAndBlocksDuplicate() {
        Student s = newStudent("学生甲");
        flow.submitScreen(abnormalScreen(s.getId(), "DEV-T-01"));

        Long taskId = flow.initiateRetest(init(s.getId(), "DEVICE_BLOCK")).getId();
        RetestTask t = retestRepo.findById(taskId).orElseThrow();
        assertEquals(RetestStatus.PENDING, t.getStatus());
        assertEquals("DEVICE_BLOCK", t.getReason().name());
        assertNotNull(t.getInitiator());
        assertNotNull(t.getOriginalScreen().getDeviceCalibNo());
        assertEquals(StudentStage.RETEST_PENDING, studentRepo.findById(s.getId()).orElseThrow().getStage());

        // 同一学生已有进行中复测任务，禁止重复发起
        BusinessException ex = assertThrows(BusinessException.class,
                () -> flow.initiateRetest(init(s.getId(), "DEVICE_BLOCK")));
        assertTrue(ex.getMessage().contains("进行中"));
    }

    @Test
    void initiateRejectsNormalOrAbsentScreen() {
        Student s = newStudent("学生乙");
        flow.submitScreen(screenReq(s.getId(), "DEV-T-02", "5.0", "5.0", "0.00", "0.00"));
        assertThrows(BusinessException.class, () -> flow.initiateRetest(init(s.getId(), "OTHER")));
    }

    @Test
    void normalRetestClosesReferralWithoutDeletingNotification() {
        Student s = newStudent("学生丙");
        flow.submitScreen(abnormalScreen(s.getId(), "DEV-T-03"));
        // 初测异常后已发出复诊告知
        notifyParent(s.getId());
        long notifyCountBefore = confirmRepo.count();

        Long taskId = flow.initiateRetest(init(s.getId(), "DEVICE_BLOCK")).getId();
        flow.completeRetest(complete(taskId, false, "复测视力5.0，初测时设备被遮挡"));

        RetestTask t = retestRepo.findById(taskId).orElseThrow();
        assertEquals(RetestStatus.CLEARED_NORMAL, t.getStatus());
        assertNotNull(t.getConclusion());
        assertEquals(StudentStage.SCREENED, studentRepo.findById(s.getId()).orElseThrow().getStage());

        // 已发出的复诊通知未被删除，仅标记取消并留痕
        assertEquals(notifyCountBefore, confirmRepo.count());
        ParentConfirmation pc = confirmRepo.findFirstByStudentIdOrderByIdDesc(s.getId()).orElseThrow();
        assertEquals(Boolean.TRUE, pc.getCancelled());
        assertNotNull(pc.getCancelReason());
        assertNotNull(pc.getCancelTime());

        // 初测/复测两次数据均保留（含设备编号与操作人）
        List<ScreenRecord> screens = screenRepo.findByStudentIdOrderByScreenDateAscIdAsc(s.getId());
        assertEquals(2, screens.size());
        assertEquals("SCREEN", screens.get(0).getScreenRound());
        assertEquals("RETEST", screens.get(1).getScreenRound());
        assertEquals("复测操作员", screens.get(1).getOperatorName());

        // 复测异常事项已闭环
        assertTrue(issueRepo.findByStudentIdOrderByRaisedAtDesc(s.getId()).stream()
                .filter(i -> "RETEST_NEEDED".equals(i.getIssueType()))
                .allMatch(i -> "RESOLVED".equals(i.getStatus())));
    }

    @Test
    void abnormalRetestCreatesSingleReferralReminder() {
        Student s = newStudent("学生丁");
        flow.submitScreen(abnormalScreen(s.getId(), "DEV-T-04"));
        Long taskId = flow.initiateRetest(init(s.getId(), "AMBIENT_LIGHT")).getId();
        flow.completeRetest(complete(taskId, true, "复测仍异常"));

        assertEquals(RetestStatus.CONFIRMED_ABNORMAL, retestRepo.findById(taskId).orElseThrow().getStatus());
        assertEquals(StudentStage.AWAIT_PARENT, studentRepo.findById(s.getId()).orElseThrow().getStage());

        // 生成且仅生成一条未闭环复诊提醒
        assertEquals(1, confirmRepo.findAll().stream()
                .filter(c -> c.getStudent().getId().equals(s.getId())
                        && !Boolean.TRUE.equals(c.getCancelled()) && !Boolean.TRUE.equals(c.getConfirmed()))
                .count());

        // 重复提交复测结果被拒绝，不会重复创建复诊任务
        assertThrows(BusinessException.class, () -> flow.completeRetest(complete(taskId, true, null)));
        assertEquals(1, confirmRepo.findAll().stream()
                .filter(c -> c.getStudent().getId().equals(s.getId())).count());
    }

    @Test
    void abnormalRetestReusesExistingOpenReminder() {
        Student s = newStudent("学生戊");
        flow.submitScreen(abnormalScreen(s.getId(), "DEV-T-05"));
        // 已存在未闭环复诊提醒（已告知未确认）
        notifyParent(s.getId());

        Long taskId = flow.initiateRetest(init(s.getId(), "WEARING_IMPROPER")).getId();
        flow.completeRetest(complete(taskId, true, "复测仍异常"));

        // 复用既有复诊任务而非新建，避免同一学生重复创建
        assertEquals(1, confirmRepo.findAll().stream()
                .filter(c -> c.getStudent().getId().equals(s.getId())).count());
        assertEquals(StudentStage.AWAIT_PARENT, studentRepo.findById(s.getId()).orElseThrow().getStage());
    }

    @Test
    void deviceStatsFlagSuspiciousWhenRetestsClear() {
        Student a = newStudent("学生己");
        Student b = newStudent("学生庚");
        flow.submitScreen(abnormalScreen(a.getId(), "DEV-T-06"));
        flow.submitScreen(abnormalScreen(b.getId(), "DEV-T-06"));
        flow.completeRetest(complete(flow.initiateRetest(init(a.getId(), "DEVICE_BLOCK")).getId(), false, null));
        flow.completeRetest(complete(flow.initiateRetest(init(b.getId(), "DEVICE_BLOCK")).getId(), false, null));

        List<Map<String, Object>> rows = stats.retestStats("device", null);
        Map<String, Object> row = rows.stream()
                .filter(r -> "DEV-T-06".equals(r.get("deviceCalibNo")))
                .findFirst().orElseThrow();
        assertEquals(2L, ((Number) row.get("retestInitiated")).longValue());
        assertEquals(2L, ((Number) row.get("retestDone")).longValue());
        assertEquals(2L, ((Number) row.get("clearedNormal")).longValue());
        assertEquals(0L, ((Number) row.get("confirmedAbnormal")).longValue());
        assertEquals(true, row.get("suspicious"));

        // 按班级分组同样可查
        List<Map<String, Object>> byClass = stats.retestStats("class", null);
        assertTrue(byClass.stream().anyMatch(r -> "测试小学".equals(r.get("schoolName"))
                && ((Number) r.get("retestInitiated")).longValue() == 2L));
    }
}
