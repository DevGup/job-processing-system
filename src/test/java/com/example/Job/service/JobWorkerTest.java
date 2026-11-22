package com.example.Job.service;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobWorkerTest {

    private final JobService jobService = mock(JobService.class);
    private final JobPublisher jobPublisher = mock(JobPublisher.class);
    private final JobWorker worker = new JobWorker(jobService, jobPublisher);

    @Test
    void completesClaimedJobs() {
        JobExecution execution = new JobExecution(1L, "email", UUID.randomUUID());
        when(jobService.claimJob(1L)).thenReturn(execution);
        when(jobService.completeJob(execution)).thenReturn(true);

        worker.consume("1");

        verify(jobService).completeJob(execution);
        verify(jobPublisher, never()).publishDlq(1L);
    }

    @Test
    void routesExhaustedFailuresToTheDlq() {
        JobExecution execution = new JobExecution(2L, "fail", UUID.randomUUID());
        when(jobService.claimJob(2L)).thenReturn(execution);
        when(jobService.recordProcessingFailure(eq(execution), anyString()))
                .thenReturn(ProcessingFailureResult.SENT_TO_DLQ);

        worker.consume("2");

        verify(jobPublisher).publishDlq(2L);
    }

    @Test
    void ignoresMalformedMessagesWithoutCallingTheDatabase() {
        worker.consume("not-a-job-id");

        verify(jobService, never()).claimJob(org.mockito.ArgumentMatchers.anyLong());
    }
}
