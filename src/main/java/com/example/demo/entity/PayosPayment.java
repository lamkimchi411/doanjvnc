package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/** Phiên thanh toán PayOS; targetIds chứa các ID đơn mua, hoặc một ID đơn thuê. */
@Entity @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Table(name = "payos_payments")
public class PayosPayment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private long orderCode;
    @Column(nullable = false) private String kind; // RENTAL | SALE
    @Column(nullable = false, length = 500) private String targetIds;
    @Column(nullable = false) private String customerEmail;
    private long amount;
    private String status; // PENDING | PAID
    @Column(length = 2000) private String checkoutUrl;
    @Column(length = 120) private String paymentReference;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
}
