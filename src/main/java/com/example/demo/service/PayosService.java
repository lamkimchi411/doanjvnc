package com.example.demo.service;

import com.example.demo.entity.*;
import com.example.demo.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@Service @RequiredArgsConstructor
public class PayosService {
    private static final String PENDING = "PENDING", PAID = "PAID";
    private final PayosPaymentRepository payments;
    private final RentalOrderRepository orders;
    private final SaleInvoiceRepository sales;
    private final Operations operations;
    private final PurchaseService purchases;

    @Value("${app.payos.client-id:}") private String clientId;
    @Value("${app.payos.api-key:}") private String apiKey;
    @Value("${app.payos.checksum-key:}") private String checksumKey;
    @Value("${app.payos.public-url:}") private String publicUrl;

    public boolean enabled() {
        return nonBlank(clientId) && nonBlank(apiKey) && nonBlank(checksumKey) && publicUrl != null && publicUrl.matches("https?://.+");
    }

    @Transactional
    public PayosPayment createRental(Long orderId, String email) {
        RentalOrder order = orders.findById(orderId).orElseThrow();
        requireOwner(order.getCustomerEmail(), email);
        if (!Operations.PENDING.equals(order.getStatus()) || !"BANK".equals(order.getPaymentMethod()) || order.isHoldExpired())
            throw new IllegalArgumentException("Đơn thuê không còn chờ thanh toán PayOS.");
        return create("RENTAL", List.of(orderId), email, order.getBookingDeposit(), "CVL THUE");
    }

    @Transactional
    public PayosPayment createSales(List<Long> ids, String email) {
        if (ids == null || ids.isEmpty()) throw new IllegalArgumentException("Không có đơn mua để thanh toán.");
        var invoices = sales.findAllById(ids);
        if (invoices.size() != new HashSet<>(ids).size()) throw new IllegalArgumentException("Không tìm thấy đơn mua.");
        long amount = 0;
        for (SaleInvoice sale : invoices) {
            requireOwner(sale.getCustomerEmail(), email);
            if (!sale.isEditable() || !"BANK".equals(sale.getPaymentMethod())) throw new IllegalArgumentException("Đơn mua không còn chờ thanh toán PayOS.");
            amount = Math.addExact(amount, sale.getSalePrice());
        }
        return create("SALE", ids.stream().sorted().toList(), email, amount, "CVL MUA");
    }

    private PayosPayment create(String kind, List<Long> ids, String email, long amount, String description) {
        if (!enabled()) throw new IllegalStateException("PayOS chưa được cấu hình trên máy chủ.");
        if (amount < 2000) throw new IllegalArgumentException("Số tiền thanh toán PayOS phải từ 2.000đ.");
        var payment = PayosPayment.builder().kind(kind).targetIds(ids.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")))
            .customerEmail(email).amount(amount).status(PENDING).createdAt(LocalDateTime.now()).orderCode(1).build();
        payments.saveAndFlush(payment);
        payment.setOrderCode(1_500_000_000L + payment.getId());
        String root = publicUrl.replaceAll("/+$", "");
        String returnUrl = root + "/payments/return/" + payment.getId();
        String payload = "amount=" + amount + "&cancelUrl=" + returnUrl + "&description=" + description + "&orderCode=" + payment.getOrderCode() + "&returnUrl=" + returnUrl;
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("orderCode", payment.getOrderCode()); body.put("amount", amount); body.put("description", description);
            body.put("cancelUrl", returnUrl); body.put("returnUrl", returnUrl); body.put("signature", hmac(payload));
            JsonNode response = RestClient.create("https://api-merchant.payos.vn").post().uri("/v2/payment-requests")
                .contentType(MediaType.APPLICATION_JSON).header("x-client-id", clientId).header("x-api-key", apiKey)
                .body(body).retrieve().body(JsonNode.class);
            if (response == null || !"00".equals(response.path("code").asText()) || response.path("data").path("checkoutUrl").asText().isBlank())
                throw new IllegalStateException("PayOS không thể tạo mã thanh toán.");
            payment.setCheckoutUrl(response.path("data").path("checkoutUrl").asText());
            return payment;
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Không thể kết nối PayOS. Vui lòng thử lại sau.");
        }
    }

    @Transactional
    public void verifyAndConfirm(JsonNode body) {
        if (!enabled() || body == null || !body.path("success").asBoolean() || !"00".equals(body.path("code").asText()))
            throw new IllegalArgumentException("Webhook PayOS không hợp lệ.");
        JsonNode data = body.path("data");
        if (!constantEquals(hmac(canonical(data)), body.path("signature").asText())) throw new IllegalArgumentException("Chữ ký PayOS không hợp lệ.");
        long orderCode = data.path("orderCode").asLong(-1), amount = data.path("amount").asLong(-1);
        String reference = data.path("reference").asText("");
        PayosPayment payment = payments.findByOrderCode(orderCode).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy giao dịch PayOS."));
        if (PAID.equals(payment.getStatus())) return;
        if (amount != payment.getAmount() || reference.isBlank()) throw new IllegalArgumentException("Dữ liệu thanh toán PayOS không khớp.");
        List<Long> ids = Arrays.stream(payment.getTargetIds().split(",")).map(Long::valueOf).toList();
        if ("RENTAL".equals(payment.getKind())) {
            RentalOrder order = orders.findById(ids.get(0)).orElseThrow();
            operations.confirmBankWebhook(order.getPaymentCode(), amount, reference);
        } else if ("SALE".equals(payment.getKind())) {
            long total = 0; for (Long id : ids) total = Math.addExact(total, sales.findById(id).orElseThrow().getSalePrice());
            if (total != amount) throw new IllegalArgumentException("Số tiền đơn mua đã thay đổi.");
            for (Long id : ids) purchases.confirm(id, "BANK", reference, "PayOS");
        } else throw new IllegalArgumentException("Loại giao dịch PayOS không hợp lệ.");
        payment.setStatus(PAID); payment.setPaymentReference(reference); payment.setPaidAt(LocalDateTime.now());
    }

    public PayosPayment getOwned(Long id, String email) { var p = payments.findById(id).orElseThrow(); requireOwner(p.getCustomerEmail(), email); return p; }
    private String canonical(JsonNode data) {
        List<String> keys = new ArrayList<>(); data.fieldNames().forEachRemaining(keys::add); Collections.sort(keys);
        return keys.stream().map(k -> k + "=" + (data.path(k).isNull() ? "" : data.path(k).asText())).collect(java.util.stream.Collectors.joining("&"));
    }
    private String hmac(String value) { try { Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(checksumKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256")); return java.util.HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException("Không thể xác thực PayOS."); } }
    private boolean constantEquals(String left, String right) { return java.security.MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), Objects.toString(right, "").getBytes(StandardCharsets.UTF_8)); }
    private boolean nonBlank(String value) { return value != null && !value.isBlank(); }
    private void requireOwner(String owner, String actor) { if (!Objects.equals(owner, actor)) throw new IllegalArgumentException("Bạn không có quyền thanh toán giao dịch này."); }
}
