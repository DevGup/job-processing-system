package com.example.Job.service;

import org.junit.jupiter.api.Test;

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
        when(jobService.claimJob(1L)).thenReturn(new JobExecution(1L, "email"));

        worker.consume("1");

        verify(jobService).completeJob(1L);
        verify(jobPublisher, never()).publishDlq(1L);
    }

    @Test
    void routesExhaustedFailuresToTheDlq() {
        when(jobService.claimJob(2L)).thenReturn(new JobExecution(2L, "fail"));
        when(jobService.recordProcessingFailure(org.mockito.ArgumentMatchers.eq(2L),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(ProcessingFailureResult.SENT_TO_DLQ);

        worker.consume("2");

        verify(jobPublisher).publishDlq(2L);
    }

    @Test
    void ignoresMalformedMessagesWithoutCallingTheDatabase() {
        worker.consume("not-a-job-id");

        verify(jobService, never()).claimJob(org.mockito.ArgumentMatchers.anyLong());
    }
}
