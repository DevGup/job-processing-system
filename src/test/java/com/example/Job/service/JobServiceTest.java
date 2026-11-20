package com.example.Job.service;

import com.example.Job.entity.Job;
import com.example.Job.entity.JobStatus;
import com.example.Job.exception.JobStateException;
import com.example.Job.repo.JobRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

    @ParameterizedTest
    @CsvSource({"0, 1", "1, 2", "2, 4", "6, 64", "70, 64"})
    void failureSchedulesOneRetryWithCappedExponentialDelay(int previousRetries, long delaySeconds) {
        JobService service = new JobService(jobRepository, metricsService, eventPublisher, 100);
        Job job = storedJob(JobStatus.PROCESSING);
        job.setRetryCount(previousRetries);

        Instant beforeFailure = Instant.now();
        service.recordProcessingFailure(job.getId(), "temporary failure");
        Instant afterFailure = Instant.now();

        Instant due = job.getNextRetryAt();
        assertFalse(due.isBefore(beforeFailure.plusSeconds(delaySeconds)));
        assertFalse(due.isAfter(afterFailure.plusSeconds(delaySeconds)));
        assertEquals(JobStatus.PENDING, job.getStatus());
        assertEquals(previousRetries + 1, job.getRetryCount());
        assertEquals(ProcessingFailureResult.IGNORED, service.recordProcessingFailure(job.getId(), "duplicate"));
        assertEquals(due, job.getNextRetryAt());
        assertEquals(previousRetries + 1, job.getRetryCount());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void schedulerAndKafkaRedeliveryBothRespectFutureDeadline() {
        Job job = storedJob(JobStatus.PENDING);
        Instant due = Instant.now().plusSeconds(3600);
        job.setNextRetryAt(due);

        jobService.dispatchRetryIfDue(job.getId());
        assertNull(jobService.claimJob(job.getId()));

        assertEquals(JobStatus.PENDING, job.getStatus());
        assertEquals(due, job.getNextRetryAt());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void repeatedSchedulerPollsDispatchAnEligibleRetryOnce() {
        Job job = storedJob(JobStatus.PENDING);
        job.setNextRetryAt(Instant.EPOCH);

        jobService.dispatchRetryIfDue(job.getId());
        jobService.dispatchRetryIfDue(job.getId());

        verify(eventPublisher).publishEvent(new JobQueuedEvent(job.getId()));
        assertEquals(new JobExecution(job.getId(), job.getType()), jobService.claimJob(job.getId()));
        assertNull(jobService.claimJob(job.getId()));
        assertNull(job.getNextRetryAt());
    }

    @Test
    void legacyPendingJobWithNoDeadlineCanBeClaimedImmediately() {
        Job job = storedJob(JobStatus.PENDING);
        assertEquals(new JobExecution(job.getId(), job.getType()), jobService.claimJob(job.getId()));
    }

    @Test
    void kafkaCanClaimADueRetryBeforeSchedulerPolls() {
        Job job = storedJob(JobStatus.PENDING);
        job.setNextRetryAt(Instant.EPOCH);

        assertEquals(new JobExecution(job.getId(), job.getType()), jobService.claimJob(job.getId()));
        jobService.dispatchRetryIfDue(job.getId());

        assertEquals(JobStatus.PROCESSING, job.getStatus());
        assertNull(job.getNextRetryAt());
        verifyNoInteractions(eventPublisher);
    }

    private Job storedJob(JobStatus status) {
        Job job = Job.builder().id(12L).type("report").status(status).build();
        when(jobRepository.findByIdForUpdate(job.getId())).thenReturn(Optional.of(job));
        return job;
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
