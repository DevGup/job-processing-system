package com.example.Job.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class JobRequest {

    @NotBlank(message = "type is required")
    @Size(max = 100, message = "type must be at most 100 characters")
    private String type;

    @NotNull(message = "userId is required")
    @Positive(message = "userId must be positive")
    private Long userId;

}
