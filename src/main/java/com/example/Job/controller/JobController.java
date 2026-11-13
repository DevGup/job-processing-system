package com.example.Job.controller;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.bind.annotation.*;

import com.example.Job.dto.JobRequest;
import com.example.Job.dto.JobStatusUpdateRequest;
import com.example.Job.entity.Job;
import com.example.Job.entity.JobStatus;
import com.example.Job.service.JobService;

@RestController
@RequestMapping("/jobs")
@Validated
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping
    public ResponseEntity<Job> createJob(@Valid @RequestBody JobRequest request) {
        Job job = jobService.createJob(request.getType(), request.getUserId());
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(job.getId()).toUri()).body(job);
    }

    @PostMapping("/retry-dlq/{id}")
    public ResponseEntity<Job> retryDlq(@PathVariable @Positive Long id) {
        return ResponseEntity.accepted().body(jobService.retryDlq(id));
    }

    @GetMapping("/failed")
    public ResponseEntity<List<Job>> getFailedJobs() {

        return ResponseEntity.ok(
                jobService.getFailedJobs());
    }

    @GetMapping("/{id}")
    public Job getJob(@PathVariable @Positive Long id) {
        return jobService.getJob(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteJob(@PathVariable @Positive Long id) {
        jobService.deleteJob(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/status")
    public Job updateStatus(@PathVariable @Positive Long id, @Valid @RequestBody JobStatusUpdateRequest request) {

        JobStatus status = JobStatus.valueOf(request.getStatus().toUpperCase());

        return jobService.updateJobStatus(id, status);
    }

    @GetMapping
    public List<Job> getAllJobs() {
        return jobService.getAllJobs();
    }

   
}
