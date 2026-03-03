package com.example.phase1.dto;

import lombok.Data;

@Data
public class JobStatusUpdateRequest {

    private String status;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}