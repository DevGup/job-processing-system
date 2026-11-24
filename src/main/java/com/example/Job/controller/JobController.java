package com.example.Job.controller;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.bind.annotation.*;

import com.example.Job.dto.JobRequest;
import com.example.Job.dto.JobStatusUpdateRequest;
import com.example.Job.entity.Job;
import com.example.Job.entity.JobStatus;
import com.example.Job.service.JobService;
import com.example.Job.service.JobCaller;

@RestController
@RequestMapping("/jobs")
@Validated
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping
    public ResponseEntity<Job> createJob(@Valid @RequestBody JobRequest request, JwtAuthenticationToken authentication) {
        Job job = jobService.createJob(request.getType(), JobCaller.from(authentication).accountId());
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(job.getId()).toUri()).body(job);
    }

    @PostMapping("/retry-dlq/{id}")
    public ResponseEntity<Job> retryDlq(@PathVariable @Positive Long id) {
        return ResponseEntity.accepted().body(jobService.retryDlq(id));
    }

    @GetMapping("/failed")
    public ResponseEntity<List<Job>> getFailedJobs(JwtAuthenticationToken authentication) {
        return ResponseEntity.ok(jobService.getFailedJobs(JobCaller.from(authentication)));
    }

    @GetMapping("/{id}")
    public Job getJob(@PathVariable @Positive Long id, JwtAuthenticationToken authentication) {
        return jobService.getJob(id, JobCaller.from(authentication));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteJob(@PathVariable @Positive Long id, JwtAuthenticationToken authentication) {
        jobService.deleteJob(id, JobCaller.from(authentication));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/status")
    public Job updateStatus(@PathVariable @Positive Long id, @Valid @RequestBody JobStatusUpdateRequest request) {

        JobStatus status = JobStatus.valueOf(request.getStatus().toUpperCase());

        return jobService.updateJobStatus(id, status);
    }

    @GetMapping
    public List<Job> getAllJobs(JwtAuthenticationToken authentication) {
        return jobService.getAllJobs(JobCaller.from(authentication));
    }

   
}
