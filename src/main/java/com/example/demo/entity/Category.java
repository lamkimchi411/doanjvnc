package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Category {
 @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
 @Column(unique = true, nullable = false) private String name;
 @Column(length = 500) private String description;
}
