package com.example.Job.service;

import java.util.List;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.example.Job.entity.Job;
import com.example.Job.entity.JobStatus;
import com.example.Job.repo.JobRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor

public class JobRecovery {

    private final JobRepository jobRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverPendingJobs() {

        List<Job> jobs = jobRepository.findByStatusIn(
                List.of(
                        JobStatus.PENDING,
                        JobStatus.PROCESSING));

        System.out.println("Found " + jobs.size() + " recoverable jobs");

        for (Job job : jobs) {

            kafkaTemplate.send(
                    "job-requests",
                    job.getId().toString());

            System.out.println(
                    "Re-published Job " + job.getId());
        }
    }
}
