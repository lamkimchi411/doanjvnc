package com.example.demo.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
@Entity @Getter @Setter @NoArgsConstructor
public class InventoryEvent {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne private Product product;
 private String previousStatus;
 private String nextStatus;
 private String actor;
 private String note;
 private LocalDateTime recordedAt;
}
