package com.example.phase1.repo;


import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.phase1.entity.Job;
import com.example.phase1.entity.JobStatus;

public interface JobRepository extends JpaRepository<Job, Long> {

    List<Job> findByStatusIn(List<JobStatus> statuses);
    List<Job> findByStatus(JobStatus status);
    long countByStatus(JobStatus status);
}