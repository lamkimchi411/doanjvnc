package com.example.demo.entity;
import jakarta.persistence.*;
import lombok.*;
@Entity @Getter @Setter @NoArgsConstructor
public class OrderLine {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(optional=false) private RentalOrder rentalOrder;
 @ManyToOne(optional=false) private Product product;
 private String name;
 private String barcode;
 private long rent;
 private long deposit;
 @Column(length=1000) private String components;
 private String conditionOut;
 private String conditionReturn;
 @Column(length=1000) private String returnNote;
 private String damageCode;
 private String incidentType;
 private String damageLabel;
 private long damageFee;
}
