package com.example.demo.service;

import com.example.demo.entity.Product;
import com.example.demo.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class DemoDataCleanupService {
    private static final Set<String> DEMO_PRODUCT_IMAGES = Set.of(
        "https://images.unsplash.com/photo-1596704017254-9b121068fb31?auto=format&fit=crop&w=900&q=80",
        "https://images.unsplash.com/photo-1530789253388-582c481c54b0?auto=format&fit=crop&w=900&q=80",
        "https://images.unsplash.com/photo-1610030469983-98e550d6193c?auto=format&fit=crop&w=900&q=80",
        "https://images.unsplash.com/photo-1524250502761-1ac6f2e30d43?auto=format&fit=crop&w=900&q=80",
        "https://images.unsplash.com/photo-1617038260897-41a1f14a8ca0?auto=format&fit=crop&w=500&q=80",
        "https://images.unsplash.com/photo-1590373529824-2069d42f6e3f?auto=format&fit=crop&w=500&q=80",
        "https://images.unsplash.com/photo-1594744803329-e58b31de8bf5?auto=format&fit=crop&w=500&q=80",
        "https://images.unsplash.com/photo-1604902396830-aca29e19eefa?auto=format&fit=crop&w=900&q=80",
        "https://images.unsplash.com/photo-1515886657613-9f3515b0c78f?auto=format&fit=crop&w=900&q=80",
        "https://images.unsplash.com/photo-1496747611176-843222e1e57c?auto=format&fit=crop&w=900&q=80",
        "https://images.unsplash.com/photo-1529139574466-a303027c1d8b?auto=format&fit=crop&w=900&q=80",
        "https://images.unsplash.com/photo-1551028719-00167b16eac5?auto=format&fit=crop&w=900&q=80",
        "https://images.unsplash.com/photo-1485230895905-ec40ba36b9bc?auto=format&fit=crop&w=900&q=80",
        "https://images.unsplash.com/photo-1618375531912-867984bdfd87?auto=format&fit=crop&w=900&q=80",
        "https://images.unsplash.com/photo-1581044777550-4cfa60707c03?auto=format&fit=crop&w=900&q=80",
        "https://images.unsplash.com/photo-1542291026-7eec264c27ff?auto=format&fit=crop&w=500&q=80",
        "https://images.unsplash.com/photo-1603561596112-db1d36499b37?auto=format&fit=crop&w=500&q=80",
        "https://images.unsplash.com/photo-1584917865442-de89df76afd3?auto=format&fit=crop&w=500&q=80",
        "https://images.unsplash.com/photo-1543163521-1bf539c55dd2?auto=format&fit=crop&w=500&q=80",
        "https://images.unsplash.com/photo-1506452305024-9d59d8d5c2ee?auto=format&fit=crop&w=500&q=80"
    );

    private final ProductRepository products;
    private final RentalOrderRepository orders;
    private final OrderLineRepository lines;
    private final MoneyRepository money;
    private final EventRepository events;
    private final ReviewRepository reviews;
    private final SaleInvoiceRepository saleInvoices;

    @Transactional
    public int removeSeedProducts() {
        var demoProducts = products.findAll().stream()
            .filter(product -> product.getImageUrl() != null && DEMO_PRODUCT_IMAGES.contains(product.getImageUrl())).toList();
        if (demoProducts.isEmpty()) return 0;

        var demoIds = demoProducts.stream().map(Product::getId).collect(java.util.stream.Collectors.toSet());
        var affectedOrders = orders.findAll().stream()
            .filter(order -> demoIds.contains(order.getProduct().getId())).toList();
        var affectedOrderIds = affectedOrders.stream().map(order -> order.getId()).collect(java.util.stream.Collectors.toSet());
        var affectedLines = lines.findAll().stream()
            .filter(line -> affectedOrderIds.contains(line.getRentalOrder().getId()) || demoIds.contains(line.getProduct().getId())).toList();

        affectedOrderIds.forEach(orderId -> money.deleteAll(money.findByRentalOrderId(orderId)));
        lines.deleteAll(affectedLines);
        orders.deleteAll(affectedOrders);
        demoIds.forEach(productId -> {
            events.deleteAll(events.findByProductIdOrderByRecordedAtDesc(productId));
            reviews.deleteAll(reviews.findByProductId(productId));
        });
        saleInvoices.deleteAll(saleInvoices.findAll().stream().filter(invoice -> demoIds.contains(invoice.getProduct().getId())).toList());
        products.deleteAll(demoProducts);
        return demoProducts.size();
    }
}
