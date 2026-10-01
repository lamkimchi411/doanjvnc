# Cổ Việt Lâu

Ứng dụng quản lý cho thuê Việt phục, xây dựng bằng **Spring Boot + Thymeleaf**. Dự án dùng cấu trúc Spring/Maven chuẩn: mã Java nằm trong `src/main/java`, giao diện Thymeleaf, CSS và JavaScript nằm trong `src/main/resources`; không có dự án backend/frontend tách rời.

## Cấu trúc dự án

```
src/
├── main/
│   ├── java/com/example/demo/
│   │   ├── config/                  # Cấu hình Spring Security và dữ liệu khởi tạo
│   │   ├── controller/              # Web/MVC controllers
│   │   ├── service/                 # Nghiệp vụ thuê, kho và thanh toán
│   │   ├── repository/              # Spring Data JPA repositories
│   │   └── entity/                  # JPA entities
│   └── resources/
│       ├── templates/               # Thymeleaf views
│       ├── static/                  # CSS, JavaScript
│       ├── application.properties
│       └── data.sql
└── test/                            # Kiểm thử nghiệp vụ và web layer
```

## Chạy với MySQL Workbench local

1. Mở MySQL Workbench và bảo đảm MySQL đang chạy trên cổng `3306`.
2. Tạo kết nối local với tài khoản `root` (hoặc thay thông tin bên dưới).
3. Trong PowerShell, đặt biến môi trường nếu tài khoản có mật khẩu:

```powershell
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "mat-khau-mysql"
```

4. Chạy `mvn spring-boot:run`. Ứng dụng tự tạo database `covietlau`, các bảng và dữ liệu mẫu; sau đó mở `http://localhost:8080`.

Chuỗi kết nối mặc định nằm trong `src/main/resources/application.properties`. Có thể thay toàn bộ bằng biến `DB_URL` nếu MySQL local dùng cổng hoặc tên database khác.

## Nghiệp vụ đã có

- Duyệt/lọc Việt phục theo loại, size, màu, phong cách và khoảng ngày trống.
- Chặn đặt trùng sản phẩm khi khoảng ngày nhận–trả giao nhau.
- Giỏ đồ thêm/xóa và trang chi tiết gợi ý phụ kiện.
- Đăng ký/đăng nhập, mật khẩu mã hóa BCrypt, nút hiện/ẩn mật khẩu.
- Đặt thuê với nhận tại cửa hàng/giao tận nơi; tiền thuê theo số ngày, tiền cọc bảo đảm và cọc giữ lịch 30% tiền thuê.
- Tài khoản khách hàng hiển thị trạng thái đơn và ví cọc/hoàn cọc.
- Tính giá thuê theo từng ngày: ngày thường, cuối tuần và ngày lễ/Tết; cấu hình được trực tiếp trong trang quản trị.
- Cấu hình combo/voucher (ví dụ Nhật Bình, Áo Tấc và phụ kiện) cùng bảng phí đền bù khi mất hoặc hỏng đồ.
- Mỗi bộ đồ/phụ kiện vật lý có barcode/QR và serial riêng; theo dõi số lượt thuê, số lần giặt, trạng thái kho và lịch sử trạng thái.
- Luồng POS hoàn chỉnh: cọc giữ lịch → chuẩn bị → quét QR khi bàn giao → check-in, ghi nhận hư hỏng → khấu trừ/hoàn cọc → giặt hấp → sẵn sàng cho thuê.
- Báo cáo tỷ lệ lấp đầy theo kỳ, lượt thuê, tỷ lệ hỏng/mất, và cảnh báo phụ kiện có tỷ lệ thất thoát cao.
- Nhập nhiều bộ cùng mẫu trong một lần (1–100 bộ); mỗi bộ có serial và tem QR/Code 128 riêng.
- Báo cáo sử dụng thời điểm chuyển trạng thái để tách giờ khách mặc, trên kệ, giặt/sửa và ngừng cho thuê; quy đổi thành ngày 24 giờ. Những bộ cũ thiếu lịch sử không thể tái dựng chính xác quá khứ.
- Gộp cảnh báo phụ kiện theo tên, size, màu; tỷ lệ mất/hỏng tính trên lần nhận trả trong kỳ, đề xuất số lượng nhập bổ sung theo tồn mục tiêu.
- Check-in quét những món thực nhận; món mất phải chọn lỗi LOSS và ghi lý do. Biên bản lưu lỗi, phí và ghi chú riêng từng serial.
- Checkout luôn thu đủ tiền cọc bảo đảm. Lượt dùng voucher/combo chỉ được ghi nhận sau xác nhận nhận tiền.

## Kiểm thử

```powershell
mvn test
```
