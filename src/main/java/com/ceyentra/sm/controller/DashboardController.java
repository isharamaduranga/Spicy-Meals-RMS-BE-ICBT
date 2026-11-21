package com.ceyentra.sm.controller;

import com.ceyentra.sm.dto.common.ResponseDTO;
import com.ceyentra.sm.repository.DashboardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.Instant;

@RestController
@RequestMapping("/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {
    private final DashboardRepository dashboard;

    @GetMapping("/operations")
    public ResponseDTO<Object> operations() {
        return ResponseDTO.builder().success(true).data(dashboard.operations(Instant.now())).build();
    }

    @GetMapping("/me")
    public ResponseEntity<ResponseDTO<Object>> customer(Principal principal) {
        return dashboard.customer(principal.getName())
                .map(data -> ResponseEntity.ok(ResponseDTO.builder().success(true).data(data).build()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
