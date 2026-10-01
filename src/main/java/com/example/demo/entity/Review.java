package com.example.demo.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
@Entity @Getter @Setter @NoArgsConstructor
@Table(uniqueConstraints=@UniqueConstraint(columnNames={"order_id","product_id"}))
public class Review {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="order_id") private Long orderId;
 @ManyToOne @JoinColumn(name="product_id") private Product product;
 private String customerEmail;
 private int rating;
 @Column(length=2000) private String comment;
 private String imageUrl;
 private LocalDateTime createdAt;
}
