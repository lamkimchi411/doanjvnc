package com.example.demo.service;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.*;
import java.io.*;
import javax.imageio.ImageIO;
import java.util.UUID;
@Service public class ImageStorage {
 @Value("${app.upload-dir:uploads}") private String folder;
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
    Path root=Path.of(folder).toAbsolutePath().normalize(); Files.createDirectories(root);
    String name=UUID.randomUUID()+"."+format;
    try(var stream=file.getInputStream()){Files.copy(stream,root.resolve(name));}
    return "/uploads/"+name;
   } finally {reader.dispose();}
  } catch(IOException e){throw new IllegalArgumentException("Không thể lưu ảnh.",e);}
 }
}
