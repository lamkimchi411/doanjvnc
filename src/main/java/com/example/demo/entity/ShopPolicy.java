package com.example.demo.entity;
import jakarta.persistence.*;
import lombok.*;
@Entity @Getter @Setter @NoArgsConstructor
public class ShopPolicy {
 @Id private Long id = 1L;
 private int weekendPercent = 120;
 private int holidayPercent = 150;
 private int bookingPercent = 30;
 private int appointmentCapacity = 3;
 private int holdMinutes = 10;
 private long shippingEachWay = 30000;
 private long lateHourly = 30000;
 private long lateDaily = 300000;
 private int accessoryLossAlertPercent = 30;
 private int accessoryRestockTarget = 3;
 private int retirementRentalThreshold = 80;
 private int retirementWashThreshold = 80;
 @Column(length=2000) private String holidays = "";
 private String bankName = "";
 private String bankBin = "";
 private String bankAccount = "";
 private String bankOwner = "";
 private String homeTitle = "HỒN THIÊNG NGHÌN NĂM";
 @Column(length=1000) private String homeSubtitle = "VẺ ĐẸP HUYỀN BÍ, SANG TRỌNG CỦA CỔ PHỤC VIỆT";
 private String storeAddress = "Huế, Việt Nam";
 private String storePhone = "0901 234 567";
 private String storeMapUrl = "https://maps.google.com";
 private String storeSocialUrl = "https://facebook.com";
 private String homeVideoUrl;
 // Bản ghi cũ chưa có cột video dùng link ban đầu; chuỗi rỗng nghĩa là admin đã ẩn video.
 public String getHomeVideoUrl(){return homeVideoUrl==null?"https://www.youtube.com/watch?v=cr2MgwPmtXs":homeVideoUrl;}
 @Column(length=200) private String dayCollectionProductIds = "";
 @Column(length=200) private String nightCollectionProductIds = "";
}
