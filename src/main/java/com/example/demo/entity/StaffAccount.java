package com.example.demo.entity;
import jakarta.persistence.*;
import lombok.*;
@Entity @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StaffAccount { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id; @Column(unique=true) private String email; private String fullName; private String password; private String role; private boolean enabled; }
