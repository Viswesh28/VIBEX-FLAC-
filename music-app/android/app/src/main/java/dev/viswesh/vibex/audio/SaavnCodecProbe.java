package dev.viswesh.vibex.audio;

import android.util.Base64;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import org.json.*;

/** Independent bounded JioSaavn fallback probe. No media URL leaves the native layer. */
final class SaavnCodecProbe {
  static boolean inspect(String id) {
    if (id == null || !id.matches("[A-Za-z0-9_-]{1,128}")) return false;
    HttpURLConnection meta = null, media = null;
    try {
      URL endpoint = new URL("https://www.jiosaavn.com/api.php?__call=song.getDetails&_format=json&_marker=0&api_version=4&ctx=web6dot0&pids=" + id);
      meta = open(endpoint);
      if (meta.getResponseCode() != 200) return false;
      byte[] json = readAtMost(meta.getInputStream(), 2 * 1024 * 1024);
      JSONObject song = new JSONObject(new String(json, StandardCharsets.UTF_8)).getJSONArray("songs").getJSONObject(0);
      String encoded = song.getJSONObject("more_info").getString("encrypted_media_url");
      Cipher c = Cipher.getInstance("DES/ECB/PKCS5Padding");
      c.init(Cipher.DECRYPT_MODE, new SecretKeySpec("38346591".getBytes(StandardCharsets.UTF_8), "DES"));
      String url = new String(c.doFinal(Base64.decode(encoded, Base64.DEFAULT)), StandardCharsets.UTF_8)
          .replace("_96", "_320").replaceFirst("^http:", "https:");
      URI uri = new URI(url);
      String host = uri.getHost();
      if (!"https".equals(uri.getScheme()) || uri.getUserInfo() != null || host == null
          || (uri.getPort() != -1 && uri.getPort() != 443)
          || !(host.equals("saavncdn.com") || host.endsWith(".saavncdn.com")
              || host.endsWith(".jiosaavn.com") || host.endsWith(".akamaized.net"))) return false;
      media = open(uri.toURL());
      media.setRequestProperty("Range", "bytes=0-131071");
      int code = media.getResponseCode();
      if (code != 206 || !String.valueOf(media.getHeaderField("Content-Range")).startsWith("bytes 0-")) return false;
      return isAacMp4(readAtMost(media.getInputStream(), 131072));
    } catch (Exception ignored) { return false; }
    finally { if (meta != null) meta.disconnect(); if (media != null) media.disconnect(); }
  }
  private static HttpURLConnection open(URL url) throws IOException {
    HttpURLConnection c = (HttpURLConnection) url.openConnection();
    c.setConnectTimeout(3500); c.setReadTimeout(3500); c.setInstanceFollowRedirects(false);
    c.setRequestProperty("User-Agent", "Mozilla/5.0");
    return c;
  }
  private static byte[] readAtMost(InputStream input, int max) throws IOException {
    try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] buf = new byte[8192];
      while (out.size() < max) { int n = in.read(buf, 0, Math.min(buf.length, max-out.size())); if (n < 0) break; out.write(buf, 0, n); }
      return out.toByteArray();
    }
  }
  // Conservative bounded ISO-BMFF traversal: only an mp4a sample entry in a real
  // track, with MPEG-4 audio decoder config and AAC AudioSpecificConfig, counts.
  static boolean isAacMp4(byte[] b) { return boxes(b, 0, b.length, 0, false); }
  private static long u32(byte[] b, int p) { return ((long)(b[p]&255)<<24)|((long)(b[p+1]&255)<<16)|((long)(b[p+2]&255)<<8)|(b[p+3]&255); }
  private static String type(byte[] b, int p) { return new String(b,p,4,StandardCharsets.US_ASCII); }
  private static boolean boxes(byte[] b, int start, int end, int depth, boolean moov) {
    if (depth > 12) return false;
    for (int p = start; p + 8 <= end;) {
      long size = u32(b,p); String t = type(b,p+4);
      if (size < 8 || size > end-p) return false;
      int next = p+(int)size, payload = p+8;
      if (t.equals("moov") || t.equals("trak") || t.equals("mdia") || t.equals("minf") || t.equals("stbl")) {
        if (boxes(b,payload,next,depth+1,moov || t.equals("moov"))) return true;
      } else if (moov && t.equals("stsd") && payload + 8 <= next) {
        if (boxes(b,payload+8,next,depth+1,true)) return true;
      } else if (moov && t.equals("mp4a") && payload+28 <= next) {
        if (sampleEntry(b,payload+28,next)) return true;
      }
      p=next;
    }
    return false;
  }
  private static boolean sampleEntry(byte[] b,int start,int end) {
    for(int p=start;p+8<=end;) {
      long size=u32(b,p); if(size<8 || size>end-p) return false;
      if(type(b,p+4).equals("esds") && aacDescriptors(b,p+12,p+(int)size)) return true;
      p+=(int)size;
    }
    return false;
  }
  private static int[] descriptor(byte[] b,int p,int end) {
    if(p>=end) return null; int tag=b[p++]&255, len=0, count=0, x;
    do { if(p>=end || ++count>4) return null; x=b[p++]&255; len=(len<<7)|(x&127); } while((x&128)!=0);
    if(len>end-p) return null; return new int[]{tag,p,p+len};
  }
  private static boolean aacDescriptors(byte[] b,int start,int end) {
    int[] es=descriptor(b,start,end); if(es==null || es[0]!=3 || es[1]+3>es[2]) return false;
    int p=es[1]+2, flags=b[p++]&255;
    if((flags&0x80)!=0) p+=2;
    if((flags&0x40)!=0) { if(p>=es[2]) return false; p+=1+(b[p]&255); }
    if((flags&0x20)!=0) p+=2;
    int[] dc=descriptor(b,p,es[2]); if(dc==null || dc[0]!=4 || dc[1]+13>dc[2] || (b[dc[1]]&255)!=0x40) return false;
    int[] asc=descriptor(b,dc[1]+13,dc[2]); if(asc==null || asc[0]!=5 || asc[1]>=asc[2]) return false;
    int objectType=(b[asc[1]]&255)>>3;
    return objectType==1 || objectType==2 || objectType==3 || objectType==4 || objectType==5 || objectType==29;
  }
}
