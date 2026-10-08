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
 public boolean hasUploadedImage(String url) {
  if(url==null||!url.startsWith("/uploads/"))return false;
  Path root=Path.of(folder).toAbsolutePath().normalize();
  try {
   Path file=root.resolve(url.substring("/uploads/".length())).normalize();
   return file.getParent().equals(root)&&Files.isRegularFile(file);
  } catch(InvalidPathException exception){return false;}
 }
 public void discardNewUpload(String url) {
  if(url==null||!url.matches("/uploads/[0-9a-f-]{36}\\.(png|jpeg|jpg)"))return;
  Path root=Path.of(folder).toAbsolutePath().normalize();
  Path file=root.resolve(url.substring("/uploads/".length())).normalize();
  if(!file.getParent().equals(root))return;
  try {Files.deleteIfExists(file);} catch(IOException exception){
   org.slf4j.LoggerFactory.getLogger(ImageStorage.class).warn("Không thể dọn ảnh mới sau giao dịch thất bại.");
  }
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
    Path root=Path.of(folder).toAbsolutePath().normalize(); Files.createDirectories(root);
    String name=UUID.randomUUID()+"."+format;
    try(var stream=file.getInputStream()){Files.copy(stream,root.resolve(name));}
    return "/uploads/"+name;
   } finally {reader.dispose();}
  } catch(IOException e){throw new IllegalArgumentException("Không thể lưu ảnh.",e);}
 }
}
