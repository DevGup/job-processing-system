package com.example.Job.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.Job.entity.Job;
import com.example.Job.entity.JobStatus;
import com.example.Job.repo.JobRepository;

import com.example.Job.exception.JobNotFoundException;
import com.example.Job.exception.JobStateException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class JobService {

    private static final int MAX_RETRY_DELAY_DOUBLINGS = 6;
    private final JobRepository jobRepository;
    private final MetricsService metricsService;
    private final ApplicationEventPublisher eventPublisher;
    private final int maxRetries;

    public JobService(JobRepository jobRepository, MetricsService metricsService,
            ApplicationEventPublisher eventPublisher,
            @org.springframework.beans.factory.annotation.Value("${app.jobs.max-retries:3}") int maxRetries) {
        this.jobRepository = jobRepository;
        this.metricsService = metricsService;
        this.eventPublisher = eventPublisher;
        this.maxRetries = maxRetries;
    }

    @Transactional
    public Job createJob(String type, long accountId) {

        Job job = new Job();
        job.setUserId(accountId);
        job.setOwnerId(accountId);
        job.setType(type);
        job.setStatus(JobStatus.PENDING);
        job.setCreatedAt(LocalDateTime.now());
        job.setRetryCount(0);
        job.setFailedAt(null);
        job.setFailureReason(null);
        job.setProcessingStartedAt(null);
        Job savedJob = jobRepository.save(job);
        metricsService.incrementJobsCreated();

        requestDispatch(savedJob.getId());

        return savedJob;
    }

    @Transactional(readOnly = true)
    public Job getJob(Long id, JobCaller caller) {
        return jobRepository.findById(id)
                .filter(job -> caller.canAccess(job.getOwnerId()))
                .orElseThrow(() -> new JobNotFoundException(id));
    }

    @Transactional
    public void deleteJob(Long id, JobCaller caller) {
        Job job = jobRepository.findByIdForUpdate(id)
                .filter(stored -> caller.canAccess(stored.getOwnerId()))
                .orElseThrow(() -> new JobNotFoundException(id));
        if (job.getStatus() == JobStatus.PROCESSING) {
            throw new JobStateException("A processing job cannot be deleted");
        }
        jobRepository.delete(job);
    }

    /**
     * Administrative state override. Normal job execution is handled by the worker.
     */
    @Transactional
    public Job updateJobStatus(Long jobId, JobStatus status) {
        Job job = findJobForUpdate(jobId);
        if (job.getStatus() == status) {
            return job;
        }
        if (!isAllowedTransition(job.getStatus(), status)) {
            throw new JobStateException("Cannot change a job from %s to %s".formatted(job.getStatus(), status));
        }
        if (status == JobStatus.PROCESSING && isWaitingForRetry(job)) {
            throw new JobStateException("Job is waiting for its next retry");
        }
        job.setStatus(status);
        job.setAttemptId(null);
        job.setNextRetryAt(null);
        if (status == JobStatus.PROCESSING) {
            job.setProcessingStartedAt(LocalDateTime.now());
        } else {
            job.setProcessingStartedAt(null);
        }
        if (status == JobStatus.PENDING) {
            requestDispatch(jobId);
        }
        if (status == JobStatus.FAILED) {
            job.setFailedAt(LocalDateTime.now());
            job.setFailureReason("Manually marked as failed");
            metricsService.incrementJobsFailed();
        }
        if (status == JobStatus.DONE) {
            job.setFailureReason(null);
            job.setFailedAt(null);
            metricsService.incrementJobsProcessed();
        }
        return job;
    }

    @Transactional
    public Job retryDlq(Long jobId) {
        Job job = findJobForUpdate(jobId);
        if (job.getStatus() != JobStatus.FAILED) {
            throw new JobStateException("Only failed jobs can be retried");
        }

        job.setRetryCount(0);
        job.setStatus(JobStatus.PENDING);
        job.setNextRetryAt(null);
        job.setFailureReason(null);
        job.setFailedAt(null);
        job.setProcessingStartedAt(null);
        requestDispatch(jobId);
        return job;
    }

    @Transactional(readOnly = true)
    public List<Job> getFailedJobs(JobCaller caller) {
        return caller.admin() ? jobRepository.findByStatus(JobStatus.FAILED)
                : jobRepository.findByOwnerIdAndStatus(caller.accountId(), JobStatus.FAILED);
    }

    @Transactional(readOnly = true)
    public List<Job> getAllJobs(JobCaller caller) {
        return caller.admin() ? jobRepository.findAll() : jobRepository.findByOwnerId(caller.accountId());
    }

    @Transactional
    public JobExecution claimJob(Long jobId) {
        Job job = jobRepository.findByIdForUpdate(jobId).orElse(null);
        if (job == null || job.getStatus() != JobStatus.PENDING || isWaitingForRetry(job)) {
            return null;
        }
        job.setStatus(JobStatus.PROCESSING);
        job.setAttemptId(UUID.randomUUID());
        job.setNextRetryAt(null);
        job.setProcessingStartedAt(LocalDateTime.now());
        return new JobExecution(job.getId(), job.getType(), job.getAttemptId());
    }

    @Transactional
    public boolean completeJob(JobExecution execution) {
        Job job = findCurrentAttemptForUpdate(execution);
        if (job == null) {
            return false;
        }
        if ("report".equalsIgnoreCase(job.getType())) {
            long completedJobs = jobRepository.countByOwnerIdAndUserIdAndStatus(
                    job.getOwnerId(), job.getUserId(), JobStatus.DONE);
            long failedJobs = jobRepository.countByOwnerIdAndUserIdAndStatus(
                    job.getOwnerId(), job.getUserId(), JobStatus.FAILED);
            job.setResult("Completed jobs: " + completedJobs + ", Failed jobs: " + failedJobs);
        }
        job.setStatus(JobStatus.DONE);
        job.setAttemptId(null);
        job.setProcessingStartedAt(null);
        job.setFailureReason(null);
        job.setFailedAt(null);
        metricsService.incrementJobsProcessed();
        return true;
    }

    @Transactional
    public ProcessingFailureResult recordProcessingFailure(JobExecution execution, String failureReason) {
        Job job = findCurrentAttemptForUpdate(execution);
        if (job == null) {
            return ProcessingFailureResult.IGNORED;
        }

        job.setFailureReason(failureReason);
        job.setAttemptId(null);
        job.setProcessingStartedAt(null);
        if (job.getRetryCount() < maxRetries) {
            long delaySeconds = 1L << Math.min(job.getRetryCount(), MAX_RETRY_DELAY_DOUBLINGS);
            job.setNextRetryAt(Instant.now().plusSeconds(delaySeconds));
            job.setRetryCount(job.getRetryCount() + 1);
            job.setStatus(JobStatus.PENDING);
            return ProcessingFailureResult.RETRY_QUEUED;
        }

        job.setStatus(JobStatus.FAILED);
        job.setFailedAt(LocalDateTime.now());
        metricsService.incrementJobsFailed();
        metricsService.incrementJobsDlq();
        return ProcessingFailureResult.SENT_TO_DLQ;
    }

    @Transactional
    public void dispatchRetryIfDue(Long jobId) {
        Job job = jobRepository.findByIdForUpdate(jobId).orElse(null);
        if (job == null || job.getStatus() != JobStatus.PENDING
                || job.getNextRetryAt() == null || isWaitingForRetry(job)) {
            return;
        }
        job.setNextRetryAt(null);
        requestDispatch(jobId);
    }

    private boolean isWaitingForRetry(Job job) {
        return job.getNextRetryAt() != null && job.getNextRetryAt().isAfter(Instant.now());
    }

    @Transactional
    public boolean requeueStaleJob(Long jobId, LocalDateTime expiry) {
        Job job = jobRepository.findByIdForUpdate(jobId).orElse(null);
        if (job == null || job.getStatus() != JobStatus.PROCESSING || job.getProcessingStartedAt() == null
                || !job.getProcessingStartedAt().isBefore(expiry)) {
            return false;
        }
        job.setStatus(JobStatus.PENDING);
        job.setAttemptId(null);
        job.setProcessingStartedAt(null);
        job.setFailureReason("Recovered after the worker lease expired");
        requestDispatch(jobId);
        return true;
    }

    private Job findCurrentAttemptForUpdate(JobExecution execution) {
        return jobRepository.findByIdForUpdate(execution.jobId())
                .filter(job -> job.getStatus() == JobStatus.PROCESSING && job.getAttemptId() != null
                        && job.getAttemptId().equals(execution.attemptId()))
                .orElse(null);
    }

    private Job findJobForUpdate(Long id) {
        return jobRepository.findByIdForUpdate(id).orElseThrow(() -> new JobNotFoundException(id));
    }

    private void requestDispatch(Long jobId) {
        eventPublisher.publishEvent(new JobQueuedEvent(jobId));
    }

    private boolean isAllowedTransition(JobStatus current, JobStatus next) {
        return switch (current) {
            case PENDING -> next == JobStatus.PROCESSING;
            case PROCESSING -> next == JobStatus.PENDING || next == JobStatus.DONE || next == JobStatus.FAILED;
            case FAILED -> next == JobStatus.PENDING;
            case DONE -> false;
        };
    }
}
