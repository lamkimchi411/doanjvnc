package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Builder.Default private java.time.LocalDateTime createdAt = java.time.LocalDateTime.now();
    private String name;
    private String category;
    private String size;
    private String color;
    private String style;
    private long dailyPrice;
    private Long salePrice;
    private long depositAmount;
    private boolean accessory;
    private String imageUrl;
    @Column(unique = true) private String barcode;
    private String stockStatus;
    private int washCount;
    private int rentalCount;
    @Column(length = 1000) private String components;
    @Column(length = 1000) private String description;
}
