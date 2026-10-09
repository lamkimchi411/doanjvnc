package com.example.demo.service;
import org.springframework.stereotype.Service;
import com.example.demo.entity.UploadedImage;
import com.example.demo.repository.UploadedImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import javax.imageio.ImageIO;
import java.util.Optional;
import java.util.UUID;
@Service @RequiredArgsConstructor public class ImageStorage {
 private final UploadedImageRepository images;
 public record StoredImage(String contentType, byte[] content) {}
 public boolean hasUploadedImage(String url) {
  return imageId(url).filter(images::existsById).isPresent();
 }
 public void discardNewUpload(String url) {
  imageId(url).ifPresent(images::deleteById);
 }
 public Optional<StoredImage> load(String fileName) {
  return imageId("/uploads/"+fileName).flatMap(images::findById)
   .map(image->new StoredImage(image.getContentType(),image.getContent()));
 }
 public String save(MultipartFile file) {
  if(file == null || file.isEmpty()) return "";
  if(file.getSize()>10*1024*1024) throw new IllegalArgumentException("Ảnh tối đa 10 MB.");
  try(var input = ImageIO.createImageInputStream(file.getInputStream())) {
   var readers=ImageIO.getImageReaders(input);
   if(!readers.hasNext()) throw new IllegalArgumentException("Chỉ chấp nhận ảnh JPEG/PNG hợp lệ.");
   var reader=readers.next();
   try {
    reader.setInput(input); String format=reader.getFormatName().toLowerCase();
    if(!java.util.Set.of("jpeg","jpg","png").contains(format)) throw new IllegalArgumentException("Chỉ chấp nhận JPEG/PNG.");
    if((long)reader.getWidth(0)*reader.getHeight(0)>40000000) throw new IllegalArgumentException("Ảnh quá lớn.");
    String name=UUID.randomUUID()+"."+format;
    images.save(UploadedImage.builder().id(name.substring(0,name.lastIndexOf('.')))
     .contentType("image/"+(format.equals("jpg")?"jpeg":format)).content(file.getBytes()).build());
    return "/uploads/"+name;
   } finally {reader.dispose();}
  } catch(IOException e){throw new IllegalArgumentException("Không thể lưu ảnh.",e);}
 }
 private Optional<String> imageId(String url) {
  if(url==null||!url.matches("/uploads/[0-9a-f-]{36}\\.(png|jpeg|jpg)"))return Optional.empty();
  return Optional.of(url.substring("/uploads/".length(),url.lastIndexOf('.')));
 }
}
