package com.example.demo.repository;

import com.example.demo.entity.UploadedImage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UploadedImageRepository extends JpaRepository<UploadedImage, String> {
}
