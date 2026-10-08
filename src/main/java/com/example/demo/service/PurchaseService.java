package com.example.demo.service;

import com.example.demo.entity.*;
import com.example.demo.repository.*;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service @RequiredArgsConstructor
public class PurchaseService {
    private final ProductRepository products;
    private final OrderLineRepository orderLines;
    private final SaleInvoiceRepository invoices;
    private final CustomerRepository customers;
    private final EventRepository events;
    @PersistenceContext private EntityManager em;

    @Transactional
    public SaleInvoice buy(Long productId, String customerEmail, String paymentMethod, String paymentReference) {
        return buyAll(List.of(productId),customerEmail,paymentMethod,paymentReference).get(0);
    }

    @Transactional
    public List<SaleInvoice> buyAll(List<Long> productIds, String customerEmail, String paymentMethod, String paymentReference) {
        if (!Set.of("CASH", "BANK").contains(paymentMethod))
            throw new IllegalArgumentException("Chọn tiền mặt hoặc chuyển khoản.");
        if (paymentReference != null && paymentReference.trim().length() > 100)
            throw new IllegalArgumentException("Mã giao dịch tối đa 100 ký tự.");
        if(productIds==null||productIds.isEmpty()||productIds.stream().anyMatch(Objects::isNull)||new HashSet<>(productIds).size()!=productIds.size())
            throw new IllegalArgumentException("Chọn ít nhất một sản phẩm khác nhau để mua.");
        String buyerName=customers.findByEmail(customerEmail).map(Customer::getFullName).orElse(customerEmail);
        return productIds.stream().map(id->buyOne(id,customerEmail,buyerName,paymentMethod,paymentReference)).toList();
    }

    /** Nhân viên tạo đơn mua tại quầy; đơn giữ trạng thái chờ thu tiền để còn chỉnh sửa. */
    @Transactional
    public SaleInvoice createByStaff(Long productId, String customerEmail, String customerName,
                                     String paymentMethod, String paymentReference) {
        String email = customerEmail == null ? "" : customerEmail.trim().toLowerCase(Locale.ROOT);
        String name = customerName == null ? "" : customerName.trim();
        if (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || name.isBlank() || name.length() > 150
            || !Set.of("CASH", "BANK").contains(paymentMethod)
            || paymentReference == null || paymentReference.trim().length() > 100) {
            throw new IllegalArgumentException("Thông tin đơn mua không hợp lệ.");
        }
        return buyOne(productId, email, name, paymentMethod, paymentReference);
    }

    private SaleInvoice buyOne(Long productId, String customerEmail, String buyerName, String paymentMethod, String paymentReference) {
        Product product=em.find(Product.class, productId, LockModeType.PESSIMISTIC_WRITE);
        if (product == null) throw new IllegalArgumentException("Không tìm thấy sản phẩm.");
        if (product.getSalePrice() == null || product.getSalePrice() <= 0)
            throw new IllegalArgumentException("Sản phẩm này chưa được mở bán.");
        if (!"AVAILABLE".equals(product.getStockStatus()) || hasActiveRental(product))
            throw new IllegalArgumentException("Sản phẩm không còn sẵn sàng để bán.");
        if (sold(productId))
            throw new IllegalArgumentException("Sản phẩm này đã có đơn mua.");

        SaleInvoice invoice=SaleInvoice.builder().product(product).customerEmail(customerEmail).customerName(buyerName)
            .productName(product.getName()).productBarcode(product.getBarcode()).salePrice(product.getSalePrice())
            .paymentMethod(paymentMethod).paymentReference(paymentReference == null ? "" : paymentReference.trim())
            .status(Operations.PENDING).issuedAt(LocalDateTime.now()).build();
        invoices.save(invoice);
        return invoice;
    }

    private boolean sold(Long id) {
        return invoices.existsByProductIdAndStatusIsNull(id) || invoices.existsByProductIdAndStatus(id,"Đã thanh toán");
    }

    private SaleInvoice locked(Long id) {
        var sale=em.find(SaleInvoice.class,id,LockModeType.PESSIMISTIC_WRITE);
        if(sale==null)throw new IllegalArgumentException("Không tìm thấy đơn mua.");
        return sale;
    }

    private void requireEditable(SaleInvoice sale,String email) {
        if(!Objects.equals(sale.getCustomerEmail(),email))throw new IllegalArgumentException("Không có quyền sửa đơn này.");
        if(!sale.isEditable())throw new IllegalArgumentException("Chỉ được sửa/xóa đơn mua chưa thanh toán.");
    }

    @Transactional
    public void update(Long id,String email,Long productId,String name,String method,String reference) {
        var sale=locked(id);requireEditable(sale,email);
        if(name==null||name.isBlank()||name.trim().length()>150||!Set.of("CASH","BANK").contains(method)
            ||reference==null||reference.trim().length()>100)throw new IllegalArgumentException("Thông tin đơn mua không hợp lệ.");
        var product=em.find(Product.class,productId,LockModeType.PESSIMISTIC_WRITE);
        if(product==null||!"AVAILABLE".equals(product.getStockStatus())||product.getSalePrice()==null
            ||product.getSalePrice()<=0||sold(productId)||hasActiveRental(product))throw new IllegalArgumentException("Sản phẩm không còn sẵn sàng để mua.");
        sale.setProduct(product);sale.setProductName(product.getName());sale.setProductBarcode(product.getBarcode());
        sale.setSalePrice(product.getSalePrice());sale.setCustomerName(name.trim());sale.setPaymentMethod(method);sale.setPaymentReference(reference.trim());
    }

    @Transactional
    public void deleteUnpaid(Long id,String email) {
        var sale=locked(id);requireEditable(sale,email);
        // Xóa khỏi danh sách khách, vẫn lưu lịch sử đơn cho cửa hàng.
        sale.setStatus(Operations.CANCELLED);sale.setCustomerDeleted(true);
    }

    /** Nhân viên / admin sửa đơn mua chưa thanh toán – không kiểm tra quyền email. */
    @Transactional
    public void updateByStaff(Long id,Long productId,String name,String method,String reference) {
        var sale=locked(id);
        updateByStaff(sale, productId, sale.getCustomerEmail(), name, method, reference);
    }

    /** Nhân viên sửa đầy đủ thông tin liên hệ và thanh toán của đơn mua chưa thu tiền. */
    @Transactional
    public void updateByStaff(Long id,Long productId,String customerEmail,String name,String method,String reference) {
        updateByStaff(locked(id), productId, customerEmail, name, method, reference);
    }

    private void updateByStaff(SaleInvoice sale,Long productId,String customerEmail,String name,String method,String reference) {
        if(!sale.isEditable())throw new IllegalArgumentException("Chỉ được sửa đơn mua chưa thanh toán.");
        String email=customerEmail==null?"":customerEmail.trim().toLowerCase(Locale.ROOT);
        if(!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")||name==null||name.isBlank()||name.trim().length()>150||!Set.of("CASH","BANK").contains(method)
            ||reference==null||reference.trim().length()>100)throw new IllegalArgumentException("Thông tin đơn mua không hợp lệ.");
        var product=em.find(Product.class,productId,LockModeType.PESSIMISTIC_WRITE);
        if(product==null||!"AVAILABLE".equals(product.getStockStatus())||product.getSalePrice()==null
            ||product.getSalePrice()<=0||sold(productId)||hasActiveRental(product))throw new IllegalArgumentException("Sản phẩm không còn sẵn sàng để mua.");
        sale.setProduct(product);sale.setProductName(product.getName());sale.setProductBarcode(product.getBarcode());
        sale.setSalePrice(product.getSalePrice());sale.setCustomerEmail(email);sale.setCustomerName(name.trim());sale.setPaymentMethod(method);sale.setPaymentReference(reference.trim());
    }

    /** Nhân viên / admin xóa đơn mua chưa thanh toán – không kiểm tra quyền email. */
    @Transactional
    public void deleteByStaff(Long id) {
        var sale=locked(id);
        if(!sale.isEditable())throw new IllegalArgumentException("Chỉ được xóa đơn mua chưa thanh toán.");
        sale.setStatus(Operations.CANCELLED);sale.setCustomerDeleted(true);
    }

    @Transactional
    public void confirm(Long id,String method,String reference,String actor) {
        var invoice=locked(id);
        if(!invoice.isEditable())throw new IllegalArgumentException("Đơn không còn chờ thanh toán.");
        if(!Set.of("CASH","BANK").contains(method)||reference==null||reference.length()>100
            ||("BANK".equals(method)&&reference.isBlank()))throw new IllegalArgumentException("Nhập phương thức và mã giao dịch hợp lệ.");
        var product=em.find(Product.class,invoice.getProduct().getId(),LockModeType.PESSIMISTIC_WRITE);
        if(!"AVAILABLE".equals(product.getStockStatus())||sold(product.getId())||hasActiveRental(product))
            throw new IllegalArgumentException("Sản phẩm đã bán hoặc có lịch thuê. Không thể xác nhận đơn.");
        invoice.setStatus("Đã thanh toán");invoice.setPaidAt(LocalDateTime.now());
        invoice.setPaymentMethod(method);invoice.setPaymentReference(reference.trim());
        InventoryEvent event=new InventoryEvent();
        event.setProduct(product);event.setPreviousStatus(product.getStockStatus());event.setNextStatus("RETIRED");
        event.setRecordedAt(LocalDateTime.now());event.setActor(actor);event.setNote("Đã bán theo đơn mua #CVL-M"+invoice.getId());
        events.save(event);
        product.setStockStatus("RETIRED");
        products.save(product);
    }

    private boolean hasActiveRental(Product product) {
        return orderLines.findByProductId(product.getId()).stream().anyMatch(line -> {
            String status=line.getRentalOrder().getStatus();
            return Set.of(Operations.RESERVED, Operations.PREPARING, Operations.RENTED).contains(status) ||
                (Operations.PENDING.equals(status) && line.getRentalOrder().getHoldUntil() != null &&
                    line.getRentalOrder().getHoldUntil().isAfter(LocalDateTime.now()));
        });
    }
}
