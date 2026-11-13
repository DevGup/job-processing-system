package com.example.Job.service;

import org.springframework.stereotype.Service;
import org.springframework.kafka.annotation.KafkaListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobWorker {

    private final JobService jobService;
    private final JobPublisher jobPublisher;

    @KafkaListener(topics = "${app.kafka.topics.requests}", groupId = "${app.kafka.consumer-group}")
    public void consume(String payload) {
        Long jobId;
        try {
            jobId = Long.parseLong(payload);
        } catch (NumberFormatException exception) {
            log.error("Discarding malformed job message: {}", payload);
            return;
        }

        JobExecution job = jobService.claimJob(jobId);
        if (job == null) {
            log.info("Skipping job {} because it is no longer pending", jobId);
            return;
        }

        try {
            if ("fail".equalsIgnoreCase(job.type())) {
                throw new IllegalStateException("Simulated job failure");
            }
            jobService.completeJob(jobId);
            log.info("Completed job {}", jobId);
        } catch (Exception exception) {
            log.error("Job {} failed", jobId, exception);
            ProcessingFailureResult result = jobService.recordProcessingFailure(jobId, exception.getMessage());
            if (result == ProcessingFailureResult.SENT_TO_DLQ) {
                jobPublisher.publishDlq(jobId);
                log.error("Job {} moved to the DLQ", jobId);
            }
        }
    }
}
