package com.example.Job.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class JobStatusUpdateRequest {

    @NotBlank(message = "status is required")
    private String status;
}
