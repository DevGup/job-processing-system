package com.example.Job.service;

public enum ProcessingFailureResult {
    RETRY_QUEUED,
    SENT_TO_DLQ,
    IGNORED
}
