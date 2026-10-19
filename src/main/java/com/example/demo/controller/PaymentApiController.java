package com.example.demo.controller;

import com.example.demo.entity.RentalOrder;
import com.example.demo.repository.RentalOrderRepository;
import com.example.demo.service.Operations;
import com.example.demo.service.PayosService;
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
    private final PayosService payos;

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

    /** URL này được khai báo trong trang quản trị PayOS; chữ ký HMAC được kiểm tra trước khi cập nhật đơn. */
    @PostMapping("/payos/webhook")
    public ResponseEntity<Map<String, Object>> payosWebhook(@RequestBody Map<String, Object> webhook) {
        try {
            payos.verifyAndConfirm(webhook);
            return ResponseEntity.ok(Map.of("code", "00", "desc", "success"));
        } catch (Exception exception) {
            // Không để lỗi kỹ thuật trả 500 khiến PayOS lặp webhook; giao dịch chỉ được cập nhật sau chữ ký hợp lệ.
            return ResponseEntity.badRequest().body(Map.of("code", "01", "desc", "invalid"));
        }
    }

    @GetMapping("/payos/{id}/status")
    public Map<String, Object> payosStatus(@PathVariable Long id, Authentication authentication) {
        var payment = payos.getOwned(id, authentication.getName());
        return Map.of("paid", "PAID".equals(payment.getStatus()), "status", payment.getStatus());
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
