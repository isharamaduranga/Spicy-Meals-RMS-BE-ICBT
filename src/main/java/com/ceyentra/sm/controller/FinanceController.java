package com.ceyentra.sm.controller;

import com.ceyentra.sm.dto.common.ResponseDTO;
import com.ceyentra.sm.enums.PaymentStatus;
import com.ceyentra.sm.repository.FinanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/v1/admin")
@RequiredArgsConstructor
public class FinanceController {
    private final FinanceRepository finance;

    @GetMapping("/payments")
    public ResponseEntity<?> payments(@RequestParam(defaultValue = "0") int page,
                                      @RequestParam(required = false) PaymentStatus status,
                                      @RequestParam(defaultValue = "") String search) {
        if (page < 0 || search.length() > 150) {
            return ResponseEntity.badRequest().build();
        }
        return ok(finance.payments(page, status, search.trim()));
    }

    @GetMapping("/payments/{id}")
    public ResponseEntity<?> payment(@PathVariable long id) {
        Optional<Map<String, Object>> payment = finance.payment(id);
        return payment.isPresent() ? ok(payment.get()) : ResponseEntity.notFound().build();
    }

    @GetMapping("/reports")
    public ResponseEntity<?> report(@RequestParam int year, @RequestParam(required = false) Integer month) {
        if (year < 1001 || year > 9998 || (month != null && (month < 1 || month > 12))) {
            return ResponseEntity.badRequest().build();
        }
        return ok(finance.report(LocalDate.of(year, month == null ? 1 : month, 1), month == null));
    }

    private ResponseEntity<?> ok(Object data) {
        return ResponseEntity.ok(ResponseDTO.builder().success(true).data(data).build());
    }
}
