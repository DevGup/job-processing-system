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
            log.debug("Skipping job {} because it is not eligible to run", jobId);
            return;
        }

        try {
            if ("fail".equalsIgnoreCase(job.type())) {
                throw new IllegalStateException("Simulated job failure");
            }
            if (jobService.completeJob(job)) {
                log.info("Completed job {}", jobId);
            }
        } catch (Exception exception) {
            ProcessingFailureResult result = jobService.recordProcessingFailure(job, exception.getMessage());
            if (result == ProcessingFailureResult.IGNORED) {
                return;
            }
            log.error("Job {} failed", jobId, exception);
            if (result == ProcessingFailureResult.SENT_TO_DLQ) {
                jobPublisher.publishDlq(jobId);
                log.error("Job {} moved to the DLQ", jobId);
            }
        }
    }
}
