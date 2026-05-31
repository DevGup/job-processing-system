package com.example.phase1.service;

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.phase1.entity.Job;
import com.example.phase1.entity.JobStatus;
import com.example.phase1.repo.JobRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;

@Service
@RequiredArgsConstructor
public class JobWorker {

    private final JobRepository jobRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private static final Logger log = LoggerFactory.getLogger(JobWorker.class);
    private static final int MAX_RETRIES = 3;

    public void processJob(Long jobId) {
        Job job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            log.warn("Job {} not found in database", jobId);
            return;
        }
        try {

            Thread.sleep(2000);

            job.setStatus(JobStatus.PROCESSING);
            jobRepository.save(job);

            Thread.sleep(10000);

            if ("fail".equalsIgnoreCase(job.getType())) {
                throw new RuntimeException("Simulated job failure");
            }
            job.setStatus(JobStatus.DONE);
            jobRepository.save(job);

        } catch (Exception e) {

            log.error("Error processing job {}", jobId, e);

            if (job.getRetryCount() < MAX_RETRIES) {
                job.setRetryCount(job.getRetryCount() + 1);
                job.setStatus(JobStatus.PENDING);
                jobRepository.save(job);
                kafkaTemplate.send("job-requests", jobId.toString());
            } else {
                job.setStatus(JobStatus.FAILED);
                jobRepository.save(job);
                log.error("Job {} reached max retry limit. Marking as FAILED.", jobId);
            }

        }
    }

    @KafkaListener(topics = "job-requests", groupId = "job-worker-group")
    public void consume(String jobId) {
        try {

            log.info(
                    "Thread {} received Job {}",
                    Thread.currentThread().getName(),
                    jobId);

            Thread.sleep(30000); // simulate delay

            processJob(Long.parseLong(jobId));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Kafka consumer interrupted", e);
        }
    }

}