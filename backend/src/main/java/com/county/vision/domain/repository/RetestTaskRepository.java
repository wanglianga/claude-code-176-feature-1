package com.county.vision.domain.repository;

import com.county.vision.domain.entity.RetestTask;
import com.county.vision.domain.enums.RetestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RetestTaskRepository extends JpaRepository<RetestTask, Long> {
    List<RetestTask> findByStudentIdOrderByIdDesc(Long studentId);

    List<RetestTask> findByStatusOrderByIdDesc(RetestStatus status);

    Optional<RetestTask> findFirstByStudentIdAndStatus(Long studentId, RetestStatus status);

    long countByStudentIdAndStatus(Long studentId, RetestStatus status);
}
