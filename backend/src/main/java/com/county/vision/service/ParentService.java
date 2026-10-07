package com.county.vision.service;

import com.county.vision.common.BusinessException;
import com.county.vision.domain.entity.Student;
import com.county.vision.domain.entity.UserAccount;
import com.county.vision.domain.repository.StudentRepository;
import com.county.vision.domain.repository.UserAccountRepository;
import com.county.vision.security.LoginUser;
import com.county.vision.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 家长可见档案：家长只能查看与本人账号手机号一致的孩子档案。
 * 家长可看到完整健康时间线、首次/复测两次数据、复测结论以及转诊（复诊提醒）通知留痕；
 * 复测恢复正常或仍异常的结论都会同步到这里，已发出的复诊通知不做无痕删除。
 */
@Service
public class ParentService {

    private final UserAccountRepository userRepo;
    private final StudentRepository studentRepo;
    private final QueryService queryService;

    public ParentService(UserAccountRepository userRepo, StudentRepository studentRepo,
                        QueryService queryService) {
        this.userRepo = userRepo;
        this.studentRepo = studentRepo;
        this.queryService = queryService;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> myChild() {
        LoginUser me = SecurityUtils.current();
        UserAccount account = userRepo.findById(me.getUserId())
                .orElseThrow(() -> new BusinessException(401, "账号不存在"));
        String phone = account.getPhone();
        if (phone == null || phone.isBlank()) {
            throw new BusinessException("当前家长账号未登记手机号，无法匹配孩子档案");
        }
        List<Student> children = studentRepo.findAll().stream()
                .filter(s -> phone.equals(s.getParentPhone()))
                .toList();
        if (children.isEmpty()) {
            throw new BusinessException("未找到与账号手机号 " + phone + " 关联的孩子档案");
        }
        // 一个家长账号默认展示其第一个孩子（演示数据家长均为一对一）
        return childView(children.get(0).getId());
    }

    /** 组装家长视图（复用档案聚合，仅保留家长可读内容） */
    @Transactional(readOnly = true)
    public Map<String, Object> childView(Long studentId) {
        Map<String, Object> full = queryService.detail(studentId);
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("studentId", full.get("id"));
        v.put("name", full.get("name"));
        v.put("gender", full.get("gender"));
        v.put("schoolName", full.get("schoolName"));
        v.put("grade", full.get("grade"));
        v.put("className", full.get("className"));
        v.put("stageLabel", full.get("stageLabel"));
        v.put("events", full.get("events"));        // 健康时间线（含复测结论）
        v.put("screens", full.get("screens"));       // 首次 + 复测两次数据
        v.put("retests", full.get("retests"));       // 复测原因/两次设备/操作人/结论
        v.put("referrals", full.get("referrals"));   // 家长复诊提醒与通知留痕
        return v;
    }
}
