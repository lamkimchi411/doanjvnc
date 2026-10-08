package com.example.demo.service;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Chỉ lấy mã video từ tên miền YouTube cho phép, không nhúng HTML do người dùng nhập. */
public final class YouTubeVideo {
 private YouTubeVideo() {}
 public static String normalize(String input) {
  if(input==null||input.isBlank())return "";
  try {
   if(input.length()>1000)throw new IllegalArgumentException();
   URI uri=URI.create(input.trim());
   if(!Set.of("https","http").contains(uri.getScheme())||uri.getUserInfo()!=null||uri.getPort()!=-1)
    throw new IllegalArgumentException();
   String host=uri.getHost()==null?"":uri.getHost().toLowerCase(java.util.Locale.ROOT);
   String path=uri.getPath(),id=null;
   if(Set.of("youtu.be","www.youtu.be").contains(host))id=path.substring(1);
   else if(Set.of("youtube.com","www.youtube.com","m.youtube.com","youtube-nocookie.com","www.youtube-nocookie.com").contains(host)){
    if("/watch".equals(path)&&uri.getRawQuery()!=null){
     for(String pair:uri.getRawQuery().split("&")){
      String[] part=pair.split("=",2);
      if(part.length==2&&part[0].equals("v")){
       if(id!=null)throw new IllegalArgumentException();
       id=URLDecoder.decode(part[1],StandardCharsets.UTF_8);
      }
     }
    }else if(path.matches("/(embed|shorts|live)/[^/]+"))id=path.substring(path.lastIndexOf('/')+1);
   }
   if(id==null||!id.matches("[A-Za-z0-9_-]{11}"))throw new IllegalArgumentException();
   return "https://www.youtube.com/watch?v="+id;
  }catch(RuntimeException exception){
   throw new IllegalArgumentException("Vui lòng nhập liên kết video YouTube hợp lệ.");
  }
 }
 public static String embed(String input){
  String url=normalize(input);
  return url.isEmpty()?"":"https://www.youtube-nocookie.com/embed/"+url.substring(url.indexOf("v=")+2);
 }
}
