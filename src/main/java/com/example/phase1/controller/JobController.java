package com.example.phase1.controller;

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
}