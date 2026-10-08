package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SaleInvoice {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional = false) private Product product;
    private String customerEmail;
    private String customerName;
    private String productName;
    private String productBarcode;
    private long salePrice;
    private String paymentMethod;
    private String paymentReference;
    private LocalDateTime issuedAt;
    private String status;
    private LocalDateTime paidAt;
    private Boolean customerDeleted;

    // Đơn cũ được lập theo luồng đã bán: không tự chuyển thành chưa thanh toán.
    public String getDisplayStatus() { return status == null ? "Đã thanh toán" : status; }
    public boolean isEditable() { return "Chờ thanh toán".equals(status) && paidAt == null && !Boolean.TRUE.equals(customerDeleted); }
}
