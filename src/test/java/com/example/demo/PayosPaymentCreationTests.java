package com.example.demo;

import com.example.demo.entity.*;
import com.example.demo.repository.*;
import com.example.demo.service.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.lang.reflect.Proxy;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class PayosPaymentCreationTests {
    @ParameterizedTest
    @ValueSource(strings = {"success", "rejected", "missing-link", "invalid-json"})
    void rentalDepositValidatesPayosResponse(String scenario) {
        var order = RentalOrder.builder().id(2L).customerEmail("customer@test.vn")
            .status(Operations.PENDING).paymentMethod("BANK").bookingDeposit(30000).build();
        var orders = (RentalOrderRepository) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[]{RentalOrderRepository.class}, (proxy, method, args) -> Optional.of(order));
        var payments = (PayosPaymentRepository) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[]{PayosPaymentRepository.class}, (proxy, method, args) -> {
                var payment = (PayosPayment) args[0];
                payment.setId(10L);
                return payment;
            });
        var service = new PayosService(payments, orders, null, null, null);
        ReflectionTestUtils.setField(service, "clientId", "test-client");
        ReflectionTestUtils.setField(service, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(service, "checksumKey", "test-checksum");
        ReflectionTestUtils.setField(service, "publicUrl", "https://shop.example");
        var builder = RestClient.builder().baseUrl("https://api-merchant.payos.vn");
        var server = MockRestServiceServer.bindTo(builder).build();
        ReflectionTestUtils.setField(service, "payosClientBuilder", builder);
        String response = switch (scenario) {
            case "success" -> "{\"code\":\"00\",\"data\":{\"checkoutUrl\":\"https://pay.payos.vn/test-checkout\"}}";
            case "rejected" -> "{\"code\":\"01\",\"data\":{\"checkoutUrl\":\"https://pay.payos.vn/test-checkout\"}}";
            case "missing-link" -> "{\"code\":\"00\",\"data\":{}}";
            default -> "not-json";
        };
        server.expect(requestTo("https://api-merchant.payos.vn/v2/payment-requests"))
            .andExpect(method(org.springframework.http.HttpMethod.POST))
            .andExpect(header("x-client-id", "test-client"))
            .andExpect(jsonPath("$.amount").value(30000))
            .andExpect(jsonPath("$.orderCode").value(1500000010))
            .andExpect(jsonPath("$.returnUrl").value("https://shop.example/payments/return/10"))
            .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        if ("success".equals(scenario)) {
            var payment = service.createRental(2L, "customer@test.vn");
            assertEquals("https://pay.payos.vn/test-checkout", payment.getCheckoutUrl());
            // Tạo link chỉ chờ thanh toán, không tự ghi nhận khách đã chuyển cọc.
            assertEquals("PENDING", payment.getStatus());
            assertEquals(30000, payment.getAmount());
        } else {
            assertThrows(IllegalStateException.class, () -> service.createRental(2L, "customer@test.vn"));
        }
        server.verify();
    }
}
