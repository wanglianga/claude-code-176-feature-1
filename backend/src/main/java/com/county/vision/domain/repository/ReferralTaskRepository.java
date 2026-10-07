package com.county.vision.domain.repository;

import com.county.vision.domain.entity.ReferralTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReferralTaskRepository extends JpaRepository<ReferralTask, Long> {

    List<ReferralTask> findByStudentIdOrderByIdDesc(Long studentId);

    /** 该学生是否已有未结束（待告知/已确认待复诊）的转诊任务，用于防重复创建 */
    Optional<ReferralTask> findFirstByStudentIdAndStatusInOrderByIdDesc(Long studentId, Collection<String> statuses);

    List<ReferralTask> findByStatusOrderByIdDesc(String status);

    List<ReferralTask> findAllByOrderByIdDesc();

    long countByStatus(String status);
}
