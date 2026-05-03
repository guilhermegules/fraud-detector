package com.rinha.frauddetector.controller;

import com.rinha.frauddetector.dto.ReadyResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/ready")
    public ResponseEntity<ReadyResponse> health() {
        return ResponseEntity.ok(new ReadyResponse("Ready"));
    }
}
