package com.example.demo.controller;

import com.example.demo.entity.RentalOrder;
import com.example.demo.repository.RentalOrderRepository;
import com.example.demo.service.Operations;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentApiController {
    private final RentalOrderRepository orders;
    private final Operations operations;

    @Value("${app.payment.webhook-secret:}")
    private String webhookSecret;

    public record BankWebhook(String transactionId, long amount, String content) {}
    public record PaymentStatus(String orderCode, boolean paid, boolean expired, String status, long amount, LocalDateTime expiresAt) {}

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> webhook(
        @RequestHeader(name = "X-Webhook-Secret", required = false) String suppliedSecret,
        @RequestBody BankWebhook webhook) {
        if (!hasValidSecret(suppliedSecret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("accepted", false));
        }
        String paymentCode = paymentCode(webhook.content());
        if (paymentCode == null) {
            return ResponseEntity.badRequest().body(Map.of("accepted", false, "reason", "Nội dung chuyển khoản không có mã đơn hợp lệ."));
        }
        try {
            operations.confirmBankWebhook(paymentCode, webhook.amount(), webhook.transactionId());
            return ResponseEntity.ok(Map.of("accepted", true, "orderCode", paymentCode));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("accepted", false, "reason", exception.getMessage()));
        }
    }

    @GetMapping("/orders/{id}/status")
    public PaymentStatus status(@PathVariable Long id, Authentication authentication) {
        RentalOrder order = orders.findById(id).orElseThrow();
        if (!StorefrontController.employee(authentication) && !Objects.equals(order.getCustomerEmail(), authentication.getName())) {
            throw new IllegalArgumentException("Bạn không có quyền xem trạng thái thanh toán của đơn này.");
        }
        boolean paid = Set.of(Operations.RESERVED, Operations.PREPARING, Operations.RENTED, Operations.RETURNED, Operations.REFUNDED).contains(order.getStatus());
        boolean expired = Operations.PENDING.equals(order.getStatus()) && order.getHoldUntil() != null && !order.getHoldUntil().isAfter(LocalDateTime.now());
        return new PaymentStatus(order.getPaymentCode(), paid, expired, order.getStatus(), order.getBookingDeposit(), order.getHoldUntil());
    }

    private boolean hasValidSecret(String suppliedSecret) {
        return webhookSecret != null && !webhookSecret.isBlank() && suppliedSecret != null
            && MessageDigest.isEqual(webhookSecret.getBytes(StandardCharsets.UTF_8), suppliedSecret.getBytes(StandardCharsets.UTF_8));
    }

    private String paymentCode(String content) {
        if (content == null) return null;
        var matcher = java.util.regex.Pattern.compile("\\bDH\\d{8}\\b", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(content);
        return matcher.find() ? matcher.group().toUpperCase() : null;
    }
}
