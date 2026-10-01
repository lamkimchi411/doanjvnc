package com.example.demo.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
@Entity @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TryOnAppointment {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 private String customerName; private String phone; private LocalDateTime appointmentAt; private String note; private String status;
 private String customerEmail;
}
