package com.example.phase1.service;

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.phase1.entity.Job;
import com.example.phase1.entity.JobStatus;
import com.example.phase1.repo.JobRepository;
import org.springframework.kafka.annotation.KafkaListener;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;

@Service
@RequiredArgsConstructor
public class JobWorker {

    private final JobRepository jobRepository;
    private static final Logger log = LoggerFactory.getLogger(JobWorker.class);

    public void processJob(Long jobId) {
        try {

            Job job = jobRepository.findById(jobId).orElseThrow();

            Thread.sleep(2000);

            job.setStatus(JobStatus.PROCESSING);
            jobRepository.save(job);

            Thread.sleep(10000);

            job.setStatus(JobStatus.DONE);
            jobRepository.save(job);

        } catch (Exception e) {
            log.error("Error processing job {}", jobId, e);

            Job job = jobRepository.findById(jobId).orElseThrow();
            job.setStatus(JobStatus.FAILED);
            jobRepository.save(job);
        }

    }

    @KafkaListener(topics = "job-requests", groupId = "job-worker-group")
    public void consume(String jobId) {
        try {
            Thread.sleep(30000); // simulate delay

            log.info("Received job {} from Kafka", jobId);

            processJob(Long.parseLong(jobId));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Kafka consumer interrupted", e);
        }
    }

}