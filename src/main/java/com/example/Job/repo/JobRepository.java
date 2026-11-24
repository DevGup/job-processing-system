package com.example.Job.repo;


import java.time.Instant;
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
    List<Job> findByOwnerId(Long ownerId);
    List<Job> findByOwnerIdAndStatus(Long ownerId, JobStatus status);
    List<Job> findByStatusAndNextRetryAtIsNullOrderByCreatedAtAsc(JobStatus status, Pageable pageable);
    @Query("select j.id from Job j where j.status = :status and j.nextRetryAt <= :now order by j.nextRetryAt, j.id")
    List<Long> findDueRetryIds(@Param("status") JobStatus status, @Param("now") Instant now, Pageable pageable);
    Page<Job> findByStatusAndProcessingStartedAtBeforeOrderByProcessingStartedAtAsc(
            JobStatus status, LocalDateTime startedBefore, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from Job j where j.id = :id")
    Optional<Job> findByIdForUpdate(@Param("id") Long id);

    long countByStatus(JobStatus status);
    long countByOwnerIdAndUserIdAndStatus(Long ownerId, Long userId, JobStatus status);
}
