package com.example.phase1.service;

import com.example.phase1.entity.Job;
import com.example.phase1.entity.JobStatus;
import com.example.phase1.repo.JobRepository;

import lombok.RequiredArgsConstructor;

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;
    private final JobWorker jobWorker;
    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    public Job createJob(String type, Long userId) {

        Job job = new Job();
        job.setUserId(userId); // set userId from context or parameter
        job.setType(type);
        job.setStatus(JobStatus.PENDING);
        job.setCreatedAt(LocalDateTime.now());

        Job savedJob = jobRepository.save(job);

        // send to worker
        log.info("Job {} created with status {}", savedJob.getId(), savedJob.getStatus());

        jobWorker.processJob(savedJob);

        return savedJob;
    }

    public Job getJob(Long id) {

        log.info("Fetching job with id {}", id);

        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Job not found"));

        log.info("Job {} found with status {}", id, job.getStatus());

        return job;
    }

    public void deleteJob(Long id) {

        log.info("Deleting job with id {}", id);

        Job job = jobRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Job {} not found for deletion", id);
                    return new RuntimeException("Job not found");
                });

        jobRepository.delete(job);

        log.info("Job {} deleted successfully", id);
    }

    public Job updateJobStatus(Long jobId, JobStatus status) {

        log.info("Updating job {} status to {}", jobId, status);
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found"));

        job.setStatus(status);

        Job updatedJob = jobRepository.save(job);
        log.info("Job {} status updated successfully to {}", jobId, status);

        return updatedJob;
    }
}