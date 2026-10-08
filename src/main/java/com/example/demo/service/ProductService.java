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
    private final RentalOrderRepository orders;
    private final OrderLineRepository orderLines;
    private final ReviewRepository reviews;
    private final SaleInvoiceRepository saleInvoices;
    private final ImageStorage storage;

    @Transactional
    public void save(Long id, int quantity, String name, String category, String size, String color,
                     String style, long price, long deposit, String components, String description,
                     boolean accessory, MultipartFile image, String barcode, Long salePrice) {
        name=required(name, "Tên sản phẩm", 150);
        category=required(category, "Danh mục", 100);
        size=required(size, "Size", 30);
        color=required(color, "Màu sắc", 100);
        style=required(style, "Phong cách", 100);
        components=optional(components, 1000);
        description=optional(description, 1000);
        barcode=barcode == null ? "" : barcode.trim().toUpperCase();
        if(price<0||deposit<0||salePrice!=null&&salePrice<=0||quantity<1||quantity>100||id!=null&&quantity!=1)
            throw new IllegalArgumentException("Tên, danh mục, giá hoặc số lượng không hợp lệ (1–100; sửa một mã chỉ được số lượng 1).");
        if(!barcode.isBlank() && (quantity != 1 || !barcode.matches("[A-Z0-9][A-Z0-9._/-]{2,99}")))
            throw new IllegalArgumentException("Mã QR/serial phải gồm 3–100 ký tự chữ, số, dấu chấm, gạch ngang, gạch dưới hoặc gạch chéo; chỉ nhập cho một mã đồ.");
        final String requestedBarcode=barcode;
        if(!requestedBarcode.isBlank()) products.findByBarcode(requestedBarcode)
            .filter(existing -> !existing.getId().equals(id))
            .ifPresent(existing -> { throw new IllegalArgumentException("Mã QR/serial đã thuộc về một sản phẩm khác."); });
        String url=storage.save(image);
        for(int i=0;i<quantity;i++) {
            Product p=id==null?new Product():products.findById(id).orElseThrow();
            if(id==null){p.setBarcode(requestedBarcode.isBlank() ? nextBarcode() : requestedBarcode);p.setStockStatus("AVAILABLE");}
            else if(!requestedBarcode.isBlank()) p.setBarcode(requestedBarcode);
            p.setName(name);p.setCategory(category);p.setSize(size);p.setColor(color);p.setStyle(style);
            p.setDailyPrice(price);p.setSalePrice(salePrice);p.setDepositAmount(deposit);p.setComponents(components);p.setDescription(description);p.setAccessory(accessory);
            if(!url.isBlank())p.setImageUrl(url);
            products.save(p);
            if(id==null){
                InventoryEvent event=new InventoryEvent();event.setProduct(p);event.setPreviousStatus("AVAILABLE");
                event.setNextStatus("AVAILABLE");event.setRecordedAt(LocalDateTime.now());event.setNote("Nhập kho: cấp serial riêng");event.setActor("ADMIN");events.save(event);
            }
        }
    }

    @Transactional
    public void delete(Long id) {
        Product product=products.findById(id).orElseThrow();
        var history=events.findByProductIdOrderByRecordedAtDesc(id);
        if(history.stream().anyMatch(event -> !"AVAILABLE".equals(event.getNextStatus())) ||
            orders.existsByProductId(id) || orderLines.existsByProductId(id) || reviews.existsByProductId(id) || saleInvoices.existsByProductId(id))
            throw new IllegalArgumentException("Không thể xóa sản phẩm đã phát sinh lịch sử thuê, đánh giá hoặc vận hành; hãy chuyển trạng thái ngừng cho thuê.");
        events.deleteAll(history);
        products.delete(product);
    }

    private String nextBarcode() {
        String candidate;
        do candidate="CVL-"+UUID.randomUUID().toString().replace("-","").toUpperCase();
        while(products.findByBarcode(candidate).isPresent());
        return candidate;
    }

    private String required(String value, String label, int maxLength) {
        String normalized=optional(value, maxLength);
        if(normalized.isBlank()) throw new IllegalArgumentException(label+" không được để trống.");
        return normalized;
    }

    private String optional(String value, int maxLength) {
        String normalized=value == null ? "" : value.trim();
        if(normalized.length()>maxLength) throw new IllegalArgumentException("Nội dung nhập quá dài.");
        return normalized;
    }
}
