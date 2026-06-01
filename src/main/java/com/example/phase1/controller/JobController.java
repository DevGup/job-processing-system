package com.example.phase1.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.phase1.dto.JobRequest;
import com.example.phase1.dto.JobStatusUpdateRequest;
import com.example.phase1.entity.Job;
import com.example.phase1.entity.JobStatus;
import com.example.phase1.service.JobService;

@RestController
@RequestMapping("/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping
    public Job createJob(@RequestBody JobRequest request) {
        return jobService.createJob(request.getType(), request.getUserId());
    }

    @PostMapping("/retry-dlq/{id}")
    public ResponseEntity<String> retryDlq(@PathVariable Long id) {
        return ResponseEntity.ok(
                jobService.retryDlq(id));
    }

    @GetMapping("/failed")
    public ResponseEntity<List<Job>> getFailedJobs() {

        return ResponseEntity.ok(
                jobService.getFailedJobs());
    }

    @GetMapping("/{id}")
    public Job getJob(@PathVariable Long id) {
        return jobService.getJob(id);
    }

    @DeleteMapping("/{id}")
    public void deleteJob(@PathVariable Long id) {
        jobService.deleteJob(id);
    }

    @PutMapping("/{id}/status")
    public Job updateStatus(@PathVariable Long id, @RequestBody JobStatusUpdateRequest request) {

        JobStatus status = JobStatus.valueOf(request.getStatus().toUpperCase());

        return jobService.updateJobStatus(id, status);
    }

    @GetMapping
    public List<Job> getAllJobs() {
        return jobService.getAllJobs();
    }

   
}