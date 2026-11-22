package com.example.Job.service;

import java.util.UUID;

public record JobExecution(Long jobId, String type, UUID attemptId) {
}
