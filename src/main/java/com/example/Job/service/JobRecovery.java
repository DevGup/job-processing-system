package com.example.Job.service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.Job.entity.JobStatus;
import com.example.Job.repo.JobRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class JobRecovery {

    private final JobRepository jobRepository;
    private final JobService jobService;
    private final JobPublisher jobPublisher;

    @Value("${app.jobs.recovery-batch-size:100}")
    private int batchSize;

    @Value("${app.jobs.processing-timeout-seconds:300}")
    private long processingTimeoutSeconds;

    @Scheduled(fixedDelay = 1000)
    public void dispatchDueRetries() {
        var dueIds = jobRepository.findDueRetryIds(JobStatus.PENDING, Instant.now(), PageRequest.of(0, batchSize));
        dueIds.forEach(jobService::dispatchRetryIfDue);
    }

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(fixedDelayString = "${app.jobs.recovery-interval-ms:30000}")
    public void recoverPendingJobs() {
        PageRequest page = PageRequest.of(0, batchSize);
        int republished = 0;
        for (var job : jobRepository.findByStatusAndNextRetryAtIsNullOrderByCreatedAtAsc(JobStatus.PENDING, page)) {
            jobPublisher.publishJob(job.getId());
            republished++;
        }

        LocalDateTime expiry = LocalDateTime.now().minusSeconds(processingTimeoutSeconds);
        int recovered = 0;
        for (var job : jobRepository.findByStatusAndProcessingStartedAtBeforeOrderByProcessingStartedAtAsc(
                JobStatus.PROCESSING, expiry, page)) {
            if (jobService.requeueStaleJob(job.getId(), expiry)) {
                recovered++;
            }
        }
        if (republished > 0 || recovered > 0) {
            log.info("Recovery dispatched {} pending jobs and requeued {} expired jobs", republished, recovered);
        }
    }
}
