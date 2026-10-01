package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Table(name = "rental_orders")
public class RentalOrder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String customerEmail;
    private LocalDate pickupDate;
    private LocalDate returnDate;
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
    private String refundReference;
    private java.time.LocalDateTime holdUntil;
    private java.time.LocalDateTime returnedAt;
    private java.time.LocalDateTime checkedOutAt;
    private java.time.LocalDateTime refundedAt;
    private String createdBy;
    @ManyToOne(optional = false) private Product product;
}
