package com.example.demo.entity;
import jakarta.persistence.*;
import lombok.*;
@Entity @Getter @Setter @NoArgsConstructor
public class PenaltyRule {
 @Id private String code;
 private String label;
 private long amount;
 private String incidentType = "DAMAGE"; // DAMAGE | LOSS
 private String nextStockStatus;
}
