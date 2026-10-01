package com.example.demo.service;

import com.example.demo.entity.Product;
import com.example.demo.entity.InventoryEvent;
import com.example.demo.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductRepository products;
    private final EventRepository events;
    private final ImageStorage storage;

    @Transactional
    public void save(Long id, int quantity, String name, String category, String size, String color,
                     String style, long price, long deposit, String components, String description,
                     boolean accessory, MultipartFile image) {
        if(name.isBlank()||category.isBlank()||price<0||deposit<0||quantity<1||quantity>100||id!=null&&quantity!=1)
            throw new IllegalArgumentException("Tên, danh mục, giá hoặc số lượng không hợp lệ (1–100; sửa một mã chỉ được số lượng 1).");
        String url=storage.save(image);
        for(int i=0;i<quantity;i++) {
            Product p=id==null?new Product():products.findById(id).orElseThrow();
            if(id==null){p.setBarcode("CVL-"+UUID.randomUUID().toString().replace("-","").toUpperCase());p.setStockStatus("AVAILABLE");}
            p.setName(name.trim());p.setCategory(category.trim());p.setSize(size);p.setColor(color);p.setStyle(style);
            p.setDailyPrice(price);p.setDepositAmount(deposit);p.setComponents(components);p.setDescription(description);p.setAccessory(accessory);
            if(!url.isBlank())p.setImageUrl(url);
            products.save(p);
            if(id==null){
                InventoryEvent event=new InventoryEvent();event.setProduct(p);event.setPreviousStatus("AVAILABLE");
                event.setNextStatus("AVAILABLE");event.setRecordedAt(LocalDateTime.now());event.setNote("Nhập kho: cấp serial riêng");event.setActor("ADMIN");events.save(event);
            }
        }
    }
}
