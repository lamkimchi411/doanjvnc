package com.example.demo;
import com.example.demo.service.YouTubeVideo;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class YouTubeVideoTests {
 @Test void acceptsSupportedLinks(){
  for(String url:java.util.List.of("https://www.youtube.com/watch?v=cr2MgwPmtXs&list=abc","https://youtu.be/cr2MgwPmtXs?si=abc",
   "https://www.youtube.com/shorts/cr2MgwPmtXs","https://www.youtube.com/embed/cr2MgwPmtXs"))
   assertEquals("https://www.youtube-nocookie.com/embed/cr2MgwPmtXs",YouTubeVideo.embed(url));
  assertEquals("",YouTubeVideo.embed(" "));
 }
 @Test void rejectsUntrustedOrMalformedLinks(){
  for(String url:java.util.List.of("javascript:alert(1)","https://youtube.com.evil.test/watch?v=cr2MgwPmtXs",
   "https://evil.test/embed/cr2MgwPmtXs","https://youtube.com/watch?v=bad","https://user@youtube.com/watch?v=cr2MgwPmtXs"))
   assertThrows(IllegalArgumentException.class,()->YouTubeVideo.normalize(url));
 }
}
