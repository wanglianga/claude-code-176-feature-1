package com.county.vision.domain.repository;

import com.county.vision.domain.entity.ParentConfirmation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ParentConfirmationRepository extends JpaRepository<ParentConfirmation, Long> {
    Optional<ParentConfirmation> findFirstByStudentIdOrderByIdDesc(Long studentId);

    /** 当前未闭环的复诊任务（未确认且未取消），用于复诊提醒去重与转诊关闭 */
    Optional<ParentConfirmation> findFirstByStudentIdAndConfirmedFalseAndCancelledFalseOrderByIdDesc(Long studentId);
}
