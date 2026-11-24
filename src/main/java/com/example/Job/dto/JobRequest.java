package com.example.Job.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class JobRequest {

    @NotBlank(message = "type is required")
    @Size(max = 100, message = "type must be at most 100 characters")
    private String type;

    @Null(message = "omit userId; the signed-in account owns the job")
    private Long userId;

}
