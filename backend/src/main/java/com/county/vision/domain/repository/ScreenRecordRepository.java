package com.county.vision.domain.repository;

import com.county.vision.domain.entity.ScreenRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ScreenRecordRepository extends JpaRepository<ScreenRecord, Long> {
    Optional<ScreenRecord> findFirstByStudentIdOrderByScreenDateDescIdDesc(Long studentId);
    Optional<ScreenRecord> findFirstByStudentIdAndScreenRoundOrderByScreenDateDescIdDesc(Long studentId, String round);
    List<ScreenRecord> findByStudentIdOrderByScreenDateAscIdAsc(Long studentId);
    long countByAbnormalTrue();

    @Query("select r from ScreenRecord r where r.screenRound = :round order by r.screenDate desc, r.id desc")
    List<ScreenRecord> findByRound(@Param("round") String round);

    @Query("select r from ScreenRecord r where r.student.school.id = :schoolId and r.screenRound = :round " +
            "order by r.screenDate desc, r.id desc")
    List<ScreenRecord> findBySchoolAndRound(@Param("schoolId") Long schoolId, @Param("round") String round);
}
