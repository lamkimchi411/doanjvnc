package com.example.demo.entity;

import jakarta.persistence.Basic;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "uploaded_images")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadedImage {
    @Id
    private String id;

    private String contentType;

    @Lob
    @Basic
    private byte[] content;
}
