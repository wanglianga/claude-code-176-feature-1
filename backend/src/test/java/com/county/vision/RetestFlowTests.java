package com.county.vision;

import com.county.vision.domain.entity.GradeClass;
import com.county.vision.domain.entity.Region;
import com.county.vision.domain.entity.School;
import com.county.vision.domain.entity.Student;
import com.county.vision.domain.enums.StudentStage;
import com.county.vision.domain.repository.*;
import com.county.vision.dto.RecordDtos;
import com.county.vision.service.FlowService;
import com.county.vision.service.RetestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 筛查异常二次确认（复测）核心业务规则：
 * 1. 复测恢复正常 → 记录异常原因、不生成家长复诊提醒、关闭本次转诊任务，学生回到已筛查；
 * 2. 复测仍异常 → 生成家长复诊提醒（转诊任务）；同一学生多次复测仍异常只保留一个未结束任务（防重复创建）；
 * 3. 两次筛查数据、设备编号、操作人员均保留。
 */
@SpringBootTest
@ActiveProfiles("test")
class RetestFlowTests {

    @Autowired private FlowService flow;
    @Autowired private RetestService retestService;
    @Autowired private StudentRepository studentRepo;
    @Autowired private SchoolRepository schoolRepo;
    @Autowired private GradeClassRepository classRepo;
    @Autowired private RegionRepository regionRepo;
    @Autowired private ScreenRecordRepository screenRepo;
    @Autowired private RetestRecordRepository retestRepo;
    @Autowired private ReferralTaskRepository referralRepo;

    private Student newStudent(String no) {
        Region r = new Region();
        r.setName("测试片区");
        r.setCode("RT-" + no);
        regionRepo.save(r);
        School s = new School();
        s.setName("测试学校" + no);
        s.setRegion(r);
        schoolRepo.save(s);
        GradeClass gc = new GradeClass();
        gc.setSchool(s);
        gc.setGrade("三年级");
        gc.setClassName("1班");
        classRepo.save(gc);
        Student st = new Student();
        st.setSchool(s);
        st.setGradeClass(gc);
        st.setStudentNo("RT" + no);
        st.setName("复测生" + no);
        st.setGender("男");
        st.setParentName("家长");
        st.setParentPhone("1390000" + no);
        st.setScreenYear(LocalDate.now().getYear());
        return studentRepo.save(st);
    }

    private RecordDtos.ScreenRequest abnormalScreen(Long sid) {
        RecordDtos.ScreenRequest req = new RecordDtos.ScreenRequest();
        req.setStudentId(sid);
        req.setScreenDate(LocalDate.now());
        req.setOrgName("视光中心");
        req.setOperatorName("筛查员甲");
        req.setDeviceCalibNo("DEV-BAD-01");
        req.setVenue("阶梯教室");
        req.setNakedVisionLeft("4.5");
        req.setNakedVisionRight("4.6");
        req.setSphereLeft("-1.75");
        req.setSphereRight("-1.50");
        return req;
    }

    private RecordDtos.RetestInitiateRequest initiate(Long sid, String reason) {
        RecordDtos.RetestInitiateRequest req = new RecordDtos.RetestInitiateRequest();
        req.setStudentId(sid);
        req.setReason(reason);
        req.setReasonDetail("测试现场因素");
        return req;
    }

    private RecordDtos.RetestSubmitRequest retestData(Long retestId, String vl, String vr, String sl, String sr) {
        RecordDtos.RetestSubmitRequest req = new RecordDtos.RetestSubmitRequest();
        req.setRetestId(retestId);
        req.setRetestDate(LocalDate.now());
        req.setOrgName("视光中心");
        req.setOperatorName("健康老师乙");
        req.setDeviceCalibNo("DEV-OK-09");
        req.setVenue("校医室");
        req.setNakedVisionLeft(vl);
        req.setNakedVisionRight(vr);
        req.setSphereLeft(sl);
        req.setSphereRight(sr);
        return req;
    }

    @Test
    void retestNormal_closesReferralAndKeepsTwoRecords() {
        Student s = newStudent("01");
        flow.submitScreen(abnormalScreen(s.getId()));

        Long rid = retestService.initiate(initiate(s.getId(), "ENV_LIGHT")).getId();
        retestService.submit(retestData(rid, "5.0", "5.0", "0.00", "0.00"));

        Student reloaded = studentRepo.findById(s.getId()).orElseThrow();
        assertEquals(StudentStage.SCREENED, reloaded.getStage(), "复测正常应回到已筛查，不进入家长告知");
        // 不产生未结束的家长复诊提醒
        assertTrue(referralRepo.findByStudentIdOrderByIdDesc(s.getId()).stream()
                .noneMatch(t -> "PENDING".equals(t.getStatus()) || "CONFIRMED".equals(t.getStatus())));
        // 两次筛查数据均保留，且第二次标记为 RETEST
        var screens = screenRepo.findByStudentIdOrderByScreenDateAscIdAsc(s.getId());
        assertEquals(2, screens.size());
        assertEquals("FIRST", screens.get(0).getScreenRound());
        assertEquals("RETEST", screens.get(1).getScreenRound());
        assertTrue(screens.get(0).getAbnormal());
        assertFalse(screens.get(1).getAbnormal());
        // 复测记录保留复测设备编号与操作人员（原始设备见 screens.get(0)）
        var rr = retestRepo.findById(rid).orElseThrow();
        assertEquals("NORMAL", rr.getConclusion());
        assertEquals("DEV-BAD-01", screens.get(0).getDeviceCalibNo());
        assertEquals("DEV-OK-09", rr.getRetestDeviceCalibNo());
        assertEquals("健康老师乙", rr.getRetestOperatorName());
    }

    @Test
    void retestStillAbnormal_createsSingleReferralEvenIfRetestedTwice() {
        Student s = newStudent("02");
        flow.submitScreen(abnormalScreen(s.getId()));

        Long rid1 = retestService.initiate(initiate(s.getId(), "DEVICE_BLOCK")).getId();
        retestService.submit(retestData(rid1, "4.5", "4.5", "-2.00", "-1.75"));

        // 第一次复测仍异常 → 恰好一条未结束转诊任务
        long openAfterFirst = referralRepo.findByStudentIdOrderByIdDesc(s.getId()).stream()
                .filter(t -> "PENDING".equals(t.getStatus())).count();
        assertEquals(1, openAfterFirst, "复测仍异常应生成一条家长复诊提醒");

        Student reloaded = studentRepo.findById(s.getId()).orElseThrow();
        assertEquals(StudentStage.AWAIT_PARENT, reloaded.getStage());

        // 再次复测仍异常（模拟谨慎起见二次核验）→ 仍只能有一条未结束任务，杜绝重复创建
        Long rid2 = retestService.initiate(initiate(s.getId(), "STATE_FLUCTUATION")).getId();
        retestService.submit(retestData(rid2, "4.6", "4.5", "-1.75", "-1.75"));
        long openAfterSecond = referralRepo.findByStudentIdOrderByIdDesc(s.getId()).stream()
                .filter(t -> "PENDING".equals(t.getStatus()) || "CONFIRMED".equals(t.getStatus())).count();
        assertEquals(1, openAfterSecond, "同一学生不得重复创建复诊任务");
    }
}
