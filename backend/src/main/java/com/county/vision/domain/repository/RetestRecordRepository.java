package com.county.vision.domain.repository;

import com.county.vision.domain.entity.RetestRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RetestRecordRepository extends JpaRepository<RetestRecord, Long> {

    List<RetestRecord> findByStudentIdOrderByIdDesc(Long studentId);

    /** 该学生当前是否已有未结案的复测（避免对同一异常重复发起） */
    Optional<RetestRecord> findFirstByStudentIdAndStatusOrderByIdDesc(Long studentId, String status);

    Optional<RetestRecord> findFirstByOriginalScreenId(Long originalScreenId);

    List<RetestRecord> findAllByOrderByIdDesc();

    List<RetestRecord> findByStatusOrderByIdDesc(String status);
}
