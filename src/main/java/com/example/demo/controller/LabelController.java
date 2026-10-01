package com.example.demo.controller;
import com.example.demo.repository.ProductRepository;
import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
@RestController @RequiredArgsConstructor public class LabelController {
 private final ProductRepository products;
 @GetMapping(value="/staff/label/{id}",produces="image/png") byte[] label(@PathVariable Long id) throws Exception {
  var product=products.findById(id).orElseThrow();
  var bits=new QRCodeWriter().encode(product.getBarcode(),BarcodeFormat.QR_CODE,240,240);
  return png(bits,240,240);
 }
 @GetMapping(value="/staff/barcode/{id}",produces="image/png") byte[] barcode(@PathVariable Long id) throws Exception {
  var product=products.findById(id).orElseThrow();
  var bits=new MultiFormatWriter().encode(product.getBarcode(),BarcodeFormat.CODE_128,420,100);
  return png(bits,420,100);
 }
 private byte[] png(BitMatrix bits,int width,int height) throws Exception {
  var image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
  for(int y=0;y<height;y++)for(int x=0;x<width;x++)image.setRGB(x,y,bits.get(x,y)?0:0xffffff);
  var output=new ByteArrayOutputStream();ImageIO.write(image,"png",output);return output.toByteArray();
 }
}
