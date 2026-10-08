package com.example.demo.service;

import com.example.demo.entity.SiteImage;
import com.example.demo.repository.SiteImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
import java.util.stream.IntStream;

@Service @RequiredArgsConstructor
public class CheckinGallery {
 private final SiteImageRepository images;
 private final ImageStorage storage;
 public static final List<String> SLOTS=IntStream.rangeClosed(1,6).mapToObj(i->"checkin-"+i).toList();

 // Sáu vị trí độc lập với ảnh sản phẩm; đọc trang không tạo dữ liệu trong database.
 public List<SiteImage> frames(){
  return SLOTS.stream().map(slot->images.findBySlot(slot).orElseGet(()->SiteImage.builder()
   .slot(slot).title("Góc check-in "+slot.substring(8)).imageUrl("").build())).toList();
 }

 @Transactional
 public void save(List<String> titles,List<MultipartFile> files,List<String> removed){
  if(titles==null&&files==null&&removed==null)return;
  if(titles==null||files==null||titles.size()!=6||files.size()!=6)
   throw new IllegalArgumentException("Cần đủ 6 vị trí ảnh check-in.");
  var deletes=removed==null?Set.<String>of():new HashSet<>(removed);
  if(!SLOTS.containsAll(deletes))throw new IllegalArgumentException("Vị trí ảnh check-in không hợp lệ.");
  for(int i=0;i<6;i++){
   if(titles.get(i)==null||titles.get(i).isBlank()||titles.get(i).trim().length()>120)
    throw new IllegalArgumentException("Chú thích ảnh cần từ 1 đến 120 ký tự.");
   if(deletes.contains(SLOTS.get(i))&&!files.get(i).isEmpty())
    throw new IllegalArgumentException("Không thể vừa xóa vừa tải ảnh mới tại cùng vị trí.");
  }
  var uploaded=new ArrayList<String>();
  // File không tự rollback cùng JPA: chỉ dọn file vừa tạo khi giao dịch thất bại.
  TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
   @Override public void afterCompletion(int status){
    if(status!=STATUS_COMMITTED)uploaded.forEach(storage::discardNewUpload);
   }
  });
  for(int i=0;i<6;i++){
   String slot=SLOTS.get(i);
   var row=images.findBySlot(slot).orElseGet(()->SiteImage.builder().slot(slot).imageUrl("").build());
   row.setTitle(titles.get(i).trim());
   if(deletes.contains(slot))row.setImageUrl("");
   else {
    String url=storage.save(files.get(i));
    if(!url.isBlank()){uploaded.add(url);row.setImageUrl(url);}
   }
   // Gỡ ảnh khỏi trang, giữ file cũ để tránh xóa ảnh có thể được dùng ở nơi khác.
   images.save(row);
  }
 }
}
