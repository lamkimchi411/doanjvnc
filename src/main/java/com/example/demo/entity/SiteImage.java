package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Table(name = "site_images")
public class SiteImage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "image_slot", unique = true, nullable = false) private String slot;
    private String imageUrl;
    private String title;
}
