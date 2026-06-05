package com.example.phase1.service;

import com.example.phase1.entity.Job;
import com.example.phase1.entity.JobStatus;
import com.example.phase1.repo.JobRepository;

import lombok.RequiredArgsConstructor;

import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;
    private static final Logger log = LoggerFactory.getLogger(JobService.class);
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final MetricsService metricsService;

    public Job createJob(String type, Long userId) {

        Job job = new Job();
        job.setUserId(userId);
        job.setType(type);
        job.setStatus(JobStatus.PENDING);
        job.setCreatedAt(LocalDateTime.now());
        job.setRetryCount(0);
        job.setFailedAt(null);
        job.setFailureReason(null);
        Job savedJob = jobRepository.save(job);
        metricsService.incrementJobsCreated();
        
        kafkaTemplate.send("job-requests", savedJob.getId().toString());

        return savedJob;
    }

    public Job getJob(Long id) {

        log.info("Fetching job with id {}", id);

        Job job = jobRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Job not found"));

        return job;
    }

    public void deleteJob(Long id) {

        Job job = jobRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Job {} not found for deletion", id);
                    return new RuntimeException("Job not found");
                });

        jobRepository.delete(job);

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

    public String retryDlq(Long jobId) {

        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found"));

        job.setRetryCount(0);
        job.setStatus(JobStatus.PENDING);

        jobRepository.save(job);

        kafkaTemplate.send(
                "job-requests",
                jobId.toString());

        return "Job re-submitted successfully";
    }

    public List<Job> getFailedJobs() {

        return jobRepository.findByStatus(
                JobStatus.FAILED);
    }

    public List<Job> getAllJobs() {
        
        return jobRepository.findAll();
    }
}