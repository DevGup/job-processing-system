package com.example.Job.repo;


import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import com.example.Job.entity.Job;
import com.example.Job.entity.JobStatus;

public interface JobRepository extends JpaRepository<Job, Long> {

    List<Job> findByStatusIn(List<JobStatus> statuses);
    List<Job> findByStatus(JobStatus status);
    Page<Job> findByStatusOrderByCreatedAtAsc(JobStatus status, Pageable pageable);
    Page<Job> findByStatusAndProcessingStartedAtBeforeOrderByProcessingStartedAtAsc(
            JobStatus status, LocalDateTime startedBefore, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from Job j where j.id = :id")
    Optional<Job> findByIdForUpdate(@Param("id") Long id);

    long countByStatus(JobStatus status);
    long countByUserIdAndStatus(Long userId, JobStatus status);
}
