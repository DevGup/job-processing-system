package com.example.Job.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class JobQueueListener {

    private final JobPublisher jobPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatch(JobQueuedEvent event) {
        jobPublisher.publishJob(event.jobId());
    }
}
