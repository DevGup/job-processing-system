package com.example.Job.service;

import com.example.Job.entity.Job;
import com.example.Job.entity.JobStatus;
import com.example.Job.exception.JobStateException;
import com.example.Job.repo.JobRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobServiceTest {

    private final JobRepository jobRepository = mock(JobRepository.class);
    private final MetricsService metricsService = mock(MetricsService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final JobService jobService = new JobService(jobRepository, metricsService, eventPublisher, 3);

    @Test
    void createsAPendingJobAndRequestsDispatchAfterCommit() {
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> {
            Job job = invocation.getArgument(0);
            job.setId(42L);
            return job;
        });

        Job job = jobService.createJob("email", 7L);

        assertEquals(42L, job.getId());
        assertEquals(JobStatus.PENDING, job.getStatus());
        assertEquals(0, job.getRetryCount());
        verify(metricsService).incrementJobsCreated();
        verify(eventPublisher).publishEvent(new JobQueuedEvent(42L));
    }

    @Test
    void retriesOnlyTerminallyFailedJobs() {
        Job job = new Job();
        job.setId(8L);
        job.setStatus(JobStatus.FAILED);
        job.setRetryCount(3);
        job.setFailureReason("broken");
        when(jobRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(job));

        Job retried = jobService.retryDlq(8L);

        assertEquals(JobStatus.PENDING, retried.getStatus());
        assertEquals(0, retried.getRetryCount());
        assertNull(retried.getFailureReason());
        verify(eventPublisher).publishEvent(new JobQueuedEvent(8L));
    }

    @Test
    void rejectsRetryForANonFailedJob() {
        Job job = new Job();
        job.setId(9L);
        job.setStatus(JobStatus.PENDING);
        when(jobRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(job));

        assertThrows(JobStateException.class, () -> jobService.retryDlq(9L));
    }

    @Test
    void sendsAJobToTheDlqAfterItsRetryBudgetIsExhausted() {
        Job job = new Job();
        job.setId(10L);
        job.setStatus(JobStatus.PROCESSING);
        job.setRetryCount(3);
        when(jobRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(job));

        ProcessingFailureResult result = jobService.recordProcessingFailure(10L, "failed");

        assertEquals(ProcessingFailureResult.SENT_TO_DLQ, result);
        assertEquals(JobStatus.FAILED, job.getStatus());
        verify(metricsService).incrementJobsFailed();
        verify(metricsService).incrementJobsDlq();
    }
}
