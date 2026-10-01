package com.example.demo.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
@Entity @Getter @Setter @NoArgsConstructor
public class MoneyEntry {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(optional=false) private RentalOrder rentalOrder;
 private String kind;
 private long amount;
 private String method;
 private String reference;
 private String actor;
 private LocalDateTime recordedAt;
}
