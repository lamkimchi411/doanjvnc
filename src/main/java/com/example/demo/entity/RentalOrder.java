package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Table(name = "rental_orders")
public class RentalOrder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String customerEmail;
    @Column(length = 16) private String customerPhone;
    private LocalDate pickupDate;
    private LocalDate returnDate;
    private java.time.LocalDateTime pickupAt;
    private java.time.LocalDateTime returnAt;
    private String fulfilment; // PICKUP | DELIVERY
    private String paymentMethod; // BANK | CASH
    private String status;
    private long rentalTotal;
    private long depositTotal;
    private long bookingDeposit;
    private String customerName;
    private String conditionOut;
    private String conditionReturn;
    private String collateralType;
    private String collateralReference;
    private long penaltyTotal;
    private long refundedDeposit;
    private long rentalPaid;
    private long securityPaid;
    private long shippingTotal;
    private long discountTotal;
    private long extraDue;
    private String address;
    private String voucherCode;
    private String bookingPaymentReference;
    @Column(unique = true, length = 32) private String paymentCode;
    private String refundReference;
    private java.time.LocalDateTime holdUntil;
    private java.time.LocalDateTime returnedAt;
    private java.time.LocalDateTime checkedOutAt;
    private java.time.LocalDateTime refundedAt;
    private String createdBy;
    private Boolean customerDeleted;
    @ManyToOne(optional = false) private Product product;

    /** Khách chỉ được sửa/xóa khi chưa thu cọc hay bất kỳ khoản thanh toán nào. */
    public boolean isEditable() { return "Chờ thanh toán".equals(status) && rentalPaid == 0 && securityPaid == 0 && !Boolean.TRUE.equals(customerDeleted); }
    /** Đơn quá hạn giữ chỗ nhưng chưa nhận cọc, dùng để cảnh báo theo từng đơn. */
    public boolean isHoldExpired() {
        return "Chờ thanh toán".equals(status) && holdUntil != null && holdUntil.isBefore(java.time.LocalDateTime.now());
    }
}
