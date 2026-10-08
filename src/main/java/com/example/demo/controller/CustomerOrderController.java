package com.example.demo.controller;

import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.SaleInvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequiredArgsConstructor
public class CustomerOrderController {
    private final SaleInvoiceRepository sales;
    private final ProductRepository products;

    @GetMapping("/account/purchases/{id}")
    public String purchaseDetail(@PathVariable Long id, Authentication authentication, Model model) {
        // Cùng trả 404 cho đơn không tồn tại và đơn của người khác, tránh lộ dữ liệu khách hàng.
        var sale = sales.findById(id)
            .filter(order -> authentication.getName().equals(order.getCustomerEmail()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("sale", sale);
        // Danh sách sản phẩm khả dụng cho form sửa đơn mua chưa thanh toán.
        model.addAttribute("availableProducts", products.findAll().stream()
            .filter(p -> "AVAILABLE".equals(p.getStockStatus()) && p.getSalePrice() != null && p.getSalePrice() > 0)
            .toList());
        return "customer-purchase-detail";
    }
}
