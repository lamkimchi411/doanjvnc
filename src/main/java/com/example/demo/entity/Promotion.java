package com.example.demo.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
@Entity @Getter @Setter @NoArgsConstructor
public class Promotion {
 @Id @Column(length=40) private String code;
 @Column(length=200) private String name; // Tên hiển thị, ví dụ: Combo Chụp Ảnh Đôi
 private int percent;
 private long minimumRent;
 private LocalDate startDate;
 private LocalDate endDate;
 private boolean active;
 private int usageLimit;
 private int usedCount;
 // Comma-separated physical categories, e.g. Nhật Bình,Áo Tấc,Phụ kiện,Phụ kiện
 @Column(length=1000) private String requiredCategories;
 @Column(length=500) private String description; // Mô tả ngắn combo/voucher
}
