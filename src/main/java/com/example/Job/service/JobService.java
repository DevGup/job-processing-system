package com.example.Job.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.Job.entity.Job;
import com.example.Job.entity.JobStatus;
import com.example.Job.repo.JobRepository;

import com.example.Job.exception.JobNotFoundException;
import com.example.Job.exception.JobStateException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class JobService {

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
    public Job createJob(String type, Long userId) {

        Job job = new Job();
        job.setUserId(userId);
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
    public Job getJob(Long id) {
        return jobRepository.findById(id).orElseThrow(() -> new JobNotFoundException(id));
    }

    @Transactional
    public void deleteJob(Long id) {
        Job job = jobRepository.findById(id).orElseThrow(() -> new JobNotFoundException(id));
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
        job.setStatus(status);
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
        job.setFailureReason(null);
        job.setFailedAt(null);
        job.setProcessingStartedAt(null);
        requestDispatch(jobId);
        return job;
    }

    @Transactional(readOnly = true)
    public List<Job> getFailedJobs() {
        return jobRepository.findByStatus(JobStatus.FAILED);
    }

    @Transactional(readOnly = true)
    public List<Job> getAllJobs() {
        return jobRepository.findAll();
    }

    @Transactional
    public JobExecution claimJob(Long jobId) {
        Job job = jobRepository.findByIdForUpdate(jobId).orElse(null);
        if (job == null || job.getStatus() != JobStatus.PENDING) {
            return null;
        }
        job.setStatus(JobStatus.PROCESSING);
        job.setProcessingStartedAt(LocalDateTime.now());
        return new JobExecution(job.getId(), job.getType());
    }

    @Transactional
    public void completeJob(Long jobId) {
        Job job = findJobForUpdate(jobId);
        if (job.getStatus() != JobStatus.PROCESSING) {
            throw new JobStateException("Only processing jobs can be completed");
        }
        if ("report".equalsIgnoreCase(job.getType())) {
            long completedJobs = jobRepository.countByUserIdAndStatus(job.getUserId(), JobStatus.DONE);
            long failedJobs = jobRepository.countByUserIdAndStatus(job.getUserId(), JobStatus.FAILED);
            job.setResult("Completed jobs: " + completedJobs + ", Failed jobs: " + failedJobs);
        }
        job.setStatus(JobStatus.DONE);
        job.setProcessingStartedAt(null);
        job.setFailureReason(null);
        job.setFailedAt(null);
        metricsService.incrementJobsProcessed();
    }

    @Transactional
    public ProcessingFailureResult recordProcessingFailure(Long jobId, String failureReason) {
        Job job = findJobForUpdate(jobId);
        if (job.getStatus() != JobStatus.PROCESSING) {
            return ProcessingFailureResult.IGNORED;
        }

        job.setFailureReason(failureReason);
        job.setProcessingStartedAt(null);
        if (job.getRetryCount() < maxRetries) {
            job.setRetryCount(job.getRetryCount() + 1);
            job.setStatus(JobStatus.PENDING);
            requestDispatch(jobId);
            return ProcessingFailureResult.RETRY_QUEUED;
        }

        job.setStatus(JobStatus.FAILED);
        job.setFailedAt(LocalDateTime.now());
        metricsService.incrementJobsFailed();
        metricsService.incrementJobsDlq();
        return ProcessingFailureResult.SENT_TO_DLQ;
    }

    @Transactional
    public void requeueStaleJob(Long jobId) {
        Job job = findJobForUpdate(jobId);
        if (job.getStatus() != JobStatus.PROCESSING) {
            return;
        }
        job.setStatus(JobStatus.PENDING);
        job.setProcessingStartedAt(null);
        job.setFailureReason("Recovered after the worker lease expired");
        requestDispatch(jobId);
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
