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
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        Job job = storedJob(JobStatus.PENDING);
        job.setRetryCount(previousRetries);
        JobExecution execution = service.claimJob(job.getId());

        Instant beforeFailure = Instant.now();
        service.recordProcessingFailure(execution, "temporary failure");
        Instant afterFailure = Instant.now();

        Instant due = job.getNextRetryAt();
        assertFalse(due.isBefore(beforeFailure.plusSeconds(delaySeconds)));
        assertFalse(due.isAfter(afterFailure.plusSeconds(delaySeconds)));
        assertEquals(JobStatus.PENDING, job.getStatus());
        assertEquals(previousRetries + 1, job.getRetryCount());
        assertEquals(ProcessingFailureResult.IGNORED, service.recordProcessingFailure(execution, "duplicate"));
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
        assertNotNull(jobService.claimJob(job.getId()));
        assertNull(jobService.claimJob(job.getId()));
        assertNull(job.getNextRetryAt());
    }

    @Test
    void legacyPendingJobWithNoDeadlineCanBeClaimedImmediately() {
        Job job = storedJob(JobStatus.PENDING);
        JobExecution execution = jobService.claimJob(job.getId());
        assertNotNull(execution.attemptId());
        assertEquals(job.getAttemptId(), execution.attemptId());
        assertEquals(job.getId(), execution.jobId());
        assertEquals(job.getType(), execution.type());
    }

    @Test
    void kafkaCanClaimADueRetryBeforeSchedulerPolls() {
        Job job = storedJob(JobStatus.PENDING);
        job.setNextRetryAt(Instant.EPOCH);

        assertNotNull(jobService.claimJob(job.getId()));
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
        Job job = storedJob(JobStatus.PENDING);
        job.setRetryCount(3);
        JobExecution execution = jobService.claimJob(job.getId());

        ProcessingFailureResult result = jobService.recordProcessingFailure(execution, "failed");

        assertEquals(ProcessingFailureResult.SENT_TO_DLQ, result);
        assertEquals(JobStatus.FAILED, job.getStatus());
        verify(metricsService).incrementJobsFailed();
        verify(metricsService).incrementJobsDlq();
    }

    @Test
    void staleWorkerCannotCompleteOrFailANewerAttempt() {
        Job job = storedJob(JobStatus.PENDING);
        JobExecution expired = jobService.claimJob(job.getId());
        LocalDateTime cutoff = LocalDateTime.now().minusHours(1);
        job.setProcessingStartedAt(cutoff.minusMinutes(1));
        assertTrue(jobService.requeueStaleJob(job.getId(), cutoff));
        JobExecution current = jobService.claimJob(job.getId());
        assertNotEquals(expired.attemptId(), current.attemptId());

        assertFalse(jobService.completeJob(expired));
        assertEquals(ProcessingFailureResult.IGNORED, jobService.recordProcessingFailure(expired, "late failure"));
        assertEquals(JobStatus.PROCESSING, job.getStatus());
        assertEquals(current.attemptId(), job.getAttemptId());
        assertEquals(0, job.getRetryCount());
        assertNull(job.getNextRetryAt());
        assertNull(job.getResult());
        verifyNoInteractions(metricsService);

        assertTrue(jobService.completeJob(current));
        assertEquals(JobStatus.DONE, job.getStatus());
    }

    @Test
    void redeliveryAndRepeatedCallbacksLeaveACompletedJobAlone() {
        Job job = storedJob(JobStatus.PENDING);
        JobExecution execution = jobService.claimJob(job.getId());
        assertTrue(jobService.completeJob(execution));
        String result = job.getResult();

        assertNull(jobService.claimJob(job.getId()));
        assertFalse(jobService.completeJob(execution));
        assertEquals(ProcessingFailureResult.IGNORED, jobService.recordProcessingFailure(execution, "late failure"));
        assertEquals(JobStatus.DONE, job.getStatus());
        assertEquals(result, job.getResult());
        assertEquals(0, job.getRetryCount());
        verify(metricsService).incrementJobsProcessed();
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void lateWorkerCallbacksForADeletedJobAreIgnored() {
        Job job = storedJob(JobStatus.PENDING);
        JobExecution execution = jobService.claimJob(job.getId());
        when(jobRepository.findByIdForUpdate(job.getId())).thenReturn(Optional.empty());

        assertFalse(jobService.completeJob(execution));
        assertEquals(ProcessingFailureResult.IGNORED, jobService.recordProcessingFailure(execution, "late failure"));
        assertNull(jobService.claimJob(job.getId()));
        verifyNoInteractions(metricsService, eventPublisher);
    }

    @Test
    void administrativeResetInvalidatesTheOriginalClaim() {
        Job job = storedJob(JobStatus.PENDING);
        JobExecution execution = jobService.claimJob(job.getId());
        jobService.updateJobStatus(job.getId(), JobStatus.PENDING);
        jobService.updateJobStatus(job.getId(), JobStatus.PROCESSING);

        assertFalse(jobService.completeJob(execution));
        assertEquals(ProcessingFailureResult.IGNORED, jobService.recordProcessingFailure(execution, "late failure"));
        assertEquals(JobStatus.PROCESSING, job.getStatus());
        assertEquals(0, job.getRetryCount());
        verifyNoInteractions(metricsService);
    }

    @Test
    void recoveryRechecksItsCutoffBeforeRequeuingAFreshClaim() {
        Job job = storedJob(JobStatus.PROCESSING);
        LocalDateTime cutoff = LocalDateTime.now().minusHours(1);
        job.setProcessingStartedAt(cutoff.minusMinutes(1));
        assertNull(job.getAttemptId());
        assertTrue(jobService.requeueStaleJob(job.getId(), cutoff));
        JobExecution current = jobService.claimJob(job.getId());

        assertFalse(jobService.requeueStaleJob(job.getId(), cutoff));

        assertEquals(JobStatus.PROCESSING, job.getStatus());
        assertEquals(current.attemptId(), job.getAttemptId());
        verify(eventPublisher).publishEvent(new JobQueuedEvent(job.getId()));
    }
}
