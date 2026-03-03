package com.example.phase1.service;

import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.example.phase1.entity.Job;
import com.example.phase1.entity.JobStatus;
import com.example.phase1.repo.JobRepository;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;


@Service
@RequiredArgsConstructor
public class JobWorker {

    private final JobRepository jobRepository;
     private static final Logger log = LoggerFactory.getLogger(JobService.class);

    @Async
    public void processJob(Job job) {
        try {

            // give time to observe PENDING state
            Thread.sleep(2000);

            // mark PROCESSING
            job.setStatus(JobStatus.PROCESSING);
            jobRepository.save(job);

            log.info("Job {} status updated to PROCESSING", job.getId());

            // simulate work
            Thread.sleep(10000);

            // mark DONE
            job.setStatus(JobStatus.DONE);
            jobRepository.save(job);

            log.info("Job {} completed successfully", job.getId());

        } 
        catch (InterruptedException e) {

            log.error("Job {} interrupted during processing", job.getId(), e);

            Thread.currentThread().interrupt(); // important practice
        }   

    }

}