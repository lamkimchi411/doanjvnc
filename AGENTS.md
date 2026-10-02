# Hướng dẫn làm việc với dự án Cổ Việt Lâu

## Phạm vi và giao tiếp

- Áp dụng cho toàn bộ dự án; đọc thêm `AGENTS.md` trong thư mục con nếu có trước khi sửa file tại đó.
- Trao đổi và viết chú thích bằng tiếng Việt, giữ đúng dấu và mã hóa UTF-8.
- Đọc code thực tế trước khi kết luận nguyên nhân. Báo rõ phần đã sửa, cách kiểm chứng và phần chưa kiểm chứng.
- Không khẳng định “hết tất cả lỗi” chỉ dựa vào bộ test hiện có.
- Khi được yêu cầu giải thích từng dòng, chú thích trực tiếp công dụng, đầu vào/đầu ra và nguyên lý của dòng hoặc biểu thức đó; tách những dòng đang gộp nhiều lệnh khi cần. Không đổi hành vi nghiệp vụ chỉ để thêm chú thích.

## Kiến trúc và vị trí code

Ứng dụng cho thuê Việt phục dùng Spring Boot MVC và Thymeleaf, không có frontend/backend tách rời. `pom.xml` hiện khai báo Spring Boot 4.1.1, Java 17, Spring Security, Spring Data JPA, Lombok và ZXing. Giao diện hiện dùng Tailwind CDN.

| Đường dẫn | Trách nhiệm |
| --- | --- |
| `src/main/java/com/example/demo/DemoApplication.java` | Điểm khởi động Spring Boot |
| `src/main/java/com/example/demo/config/` | Bảo mật, dữ liệu khởi tạo, phục vụ ảnh upload |
| `src/main/java/com/example/demo/controller/` | Nhận request, kiểm tra đầu vào, chuẩn bị model, chọn view/chuyển hướng |
| `src/main/java/com/example/demo/service/` | Nghiệp vụ thuê, thanh toán, kho, báo cáo và lưu ảnh |
| `src/main/java/com/example/demo/entity/` | Mô hình dữ liệu JPA |
| `src/main/java/com/example/demo/repository/` | Truy vấn qua Spring Data JPA |
| `src/main/resources/templates/` | Các trang Thymeleaf |
| `src/main/resources/templates/fragments.html` | Head, CSS dùng chung và navbar |
| `src/main/resources/templates/home.html` | Trang chủ, có head và bố cục riêng |
| `src/main/resources/templates/admin-home-settings.html` | Form chung cho ảnh, nội dung, bản đồ và liên hệ |
| `src/main/resources/static/` | JavaScript, CSS và ảnh tĩnh |
| `src/main/resources/application.properties` | Cấu hình chạy với MySQL |
| `src/test/` | Test nghiệp vụ/web và cấu hình H2 riêng |

`Operations` quản lý các luồng nghiệp vụ chính. Khi sửa giá thuê, cọc, trạng thái hoặc tồn kho, đọc service và test liên quan trước, không tính lại độc lập trong giao diện.

## Chạy và kiểm thử

Chạy lệnh tại thư mục chứa `pom.xml` bằng PowerShell:

```powershell
mvn spring-boot:run
mvn test
mvn '-Dtest=BusinessFlowTests' test
mvn package
```

- Nếu không có Maven trong PATH, dùng `./mvnw.cmd` thay `mvn` trên Windows.
- `.mvn/maven.config` đang tham chiếu `.maven-settings.xml`; giữ cấu hình Maven hiện có trừ khi nhiệm vụ yêu cầu thay đổi.
- Chạy ứng dụng cần MySQL; cấu hình qua `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `ADMIN_PASSWORD`. Không chép mật khẩu thực vào tài liệu, log hoặc mã nguồn mới.
- Trang chủ mặc định là `http://localhost:8080/`.
- Test dùng H2 trong bộ nhớ theo `src/test/resources/application.properties`; không đổi test sang cơ sở dữ liệu thật của người dùng.
- Kiểm tra mã thoát và báo cáo mới nhất trong `target/surefire-reports/`. Nếu lệnh còn chạy, theo dõi đến khi kết thúc; không dùng báo cáo cũ để khẳng định thành công.
- Chạy test phù hợp với thay đổi. Với thay đổi nghiệp vụ hoặc phân quyền, bổ sung kiểm thử hồi quy cho tình huống lỗi. Chỉ sửa tài liệu thì không cần chạy lại toàn bộ test.

## Quy tắc sửa code

- Giữ nguyên các chỉnh sửa có sẵn của người dùng và giới hạn thay đổi trong yêu cầu.
- Sửa mã nguồn trong `src`, không sửa bản build ở `target` hoặc dependency trong `.build-m2`.
- Không xóa ảnh upload, dữ liệu MySQL, lịch sử đơn hàng hoặc đặt lại database khi chưa được yêu cầu rõ ràng.
- Dữ liệu khởi tạo phải kiểm tra tồn tại và tránh ghi đè nội dung admin đã cập nhật khi khởi động lại.
- Giữ tên route, tên trường form, tên model và slot ảnh đồng bộ giữa controller, entity, template và test.
- Kiểm tra đầu vào ở server, kể cả khi HTML có `required`, `min` hoặc `maxlength`.
- Khi cập nhật nhiều bản ghi liên quan, dùng transaction phù hợp. Lưu file không tự rollback theo transaction của database; xử lý riêng nếu thay đổi luồng upload.
- Tránh đổi trạng thái hoặc dữ liệu qua GET. POST thành công nên chuyển hướng sang GET để refresh/Back không yêu cầu gửi lại biểu mẫu.
- Khi sửa dự toán, kiểm tra cả trường hợp quay lại sau khi đặt đơn, giỏ đã rỗng, chưa có dự toán và phiên hết hạn.

## Bảo mật và biểu mẫu

- Giữ CSRF; không tắt CSRF hoặc mở quyền `/admin/**` để né lỗi 403.
- Form POST Thymeleaf dùng `th:action="@{...}"` để Spring chèn token; kiểm tra token trong HTML đã render nếu gặp lỗi lưu.
- `/admin/**` chỉ dành cho ADMIN; `/staff/**` dành cho STAFF (admin được cấp cả hai role theo cấu hình hiện tại).
- Người dùng thường chỉ được xem/sửa dữ liệu thuộc tài khoản của mình. Kiểm tra quyền ở server, không chỉ ẩn nút trong template.
- Giữ trang chủ và tài nguyên công khai cần thiết truy cập được khi chưa đăng nhập.
- Không đưa thông báo exception nội bộ, truy vấn SQL hoặc thông tin bí mật ra giao diện lỗi.
- Không lồng form. Không đặt form làm con trực tiếp của `tr`; đặt trong `td` và dùng thuộc tính `form` để liên kết các input ở ô khác.

## Giao diện và ảnh

- Giữ phong cách Cổ Việt Lâu: đỏ rượu, vàng đồng, nền kem và chữ tiếng Việt dễ đọc.
- Kiểm tra bố cục ở màn hình nhỏ và desktop: navbar, form, bảng, ảnh và nội dung dài. Bảng rộng dùng vùng cuộn ngang riêng.
- Chú ý `home.html` có cấu hình riêng, sửa CSS ở `fragments.html` không tự áp dụng cho mọi thành phần trang chủ.
- Cài đặt trang chủ dùng một trang `/admin/home-settings` và một nút lưu; các route GET cũ `/admin/media`, `/admin/home-content` hiện cùng trả về trang này.
- File ảnh mới trong form cài đặt là tùy chọn; không chọn ảnh phải giữ ảnh hiện tại. Kiểm tra lưu nhiều ảnh cùng lúc và giới hạn tổng dung lượng request, không chỉ giới hạn từng file.
- Kiểm tra slot ảnh dùng chung trước khi thay đổi để tránh ảnh một khu vực làm đổi ngoài ý muốn khu vực khác.
- Kiểm tra ảnh bằng URL mà trình duyệt thực sự tải, cả khi đã đăng nhập và chưa đăng nhập.
- Liên kết ngoài mở tab mới dùng `rel="noopener noreferrer"`; giữ escape mặc định của Thymeleaf khi hiển thị nội dung người dùng nhập.

## Kiểm chứng và bàn giao

- Đọc `README.md`, các controller/service/template liên quan và kiểm thử hiện có trước khi triển khai.
- Với thay đổi web, kiểm tra cả route GET và POST, redirect, dữ liệu sau lưu và quyền của khách/admin/nhân viên tùy phạm vi.
- Test render không thay thế kiểm tra bố cục trong trình duyệt. Phân biệt kết quả test tự động với phần đã kiểm tra trực quan.
- MockMvc không tự mô phỏng đầy đủ error dispatch của servlet container; kiểm tra `/error` và template lỗi bằng cách phù hợp, không coi body rỗng của một response 404 là bằng chứng template hỏng.
- Kết thúc bằng mô tả ngắn những file/hành vi đã thay đổi, kiểm thử đã chạy và giới hạn còn lại. Nếu cần khởi động lại ứng dụng để nhận thay đổi, nói rõ cho người dùng.
