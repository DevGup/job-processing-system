package com.example.phase1.service;

import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@Service
public class MetricsService {

    private final Counter jobsCreated;
    private final Counter jobsProcessed;
    private final Counter jobsFailed;
    private final Counter jobsDlq;

    public MetricsService(MeterRegistry meterRegistry) {

        this.jobsCreated =
                Counter.builder("jobs_created_total")
                        .description("Total jobs created")
                        .register(meterRegistry);

        this.jobsProcessed =
                Counter.builder("jobs_processed_total")
                        .description("Total jobs processed")
                        .register(meterRegistry);

        this.jobsFailed =
                Counter.builder("jobs_failed_total")
                        .description("Total jobs failed")
                        .register(meterRegistry);

        this.jobsDlq =
                Counter.builder("jobs_dlq_total")
                        .description("Total jobs sent to DLQ")
                        .register(meterRegistry);
    }

    public void incrementJobsCreated() {
        jobsCreated.increment();
    }

    public void incrementJobsProcessed() {
        jobsProcessed.increment();
    }

    public void incrementJobsFailed() {
        jobsFailed.increment();
    }

    public void incrementJobsDlq() {
        jobsDlq.increment();
    }
}