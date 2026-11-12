package dev.viswesh.vibex.audio;
import static org.junit.Assert.*;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner.class)
public class SaavnCodecProbeTest {
  private static byte[] concat(byte[]... xs) throws Exception { ByteArrayOutputStream o=new ByteArrayOutputStream(); for(byte[] x:xs)o.write(x); return o.toByteArray(); }
  private static byte[] box(String type,byte[] data) throws Exception { int n=data.length+8; return concat(new byte[]{(byte)(n>>>24),(byte)(n>>>16),(byte)(n>>>8),(byte)n},type.getBytes(StandardCharsets.US_ASCII),data); }
  private static byte[] fixture(int objectType) throws Exception {
    byte[] asc=new byte[]{5,2,(byte)(objectType<<3),0x10};
    byte[] dec=concat(new byte[]{4,17,0x40,0x15,0,0,0,0,0,0,0,0,0,0,0},asc);
    byte[] es=concat(new byte[]{3,22,0,1,0},dec);
    byte[] entry=box("mp4a",concat(new byte[28],box("esds",concat(new byte[4],es))));
    byte[] stsd=box("stsd",concat(new byte[4],new byte[]{0,0,0,1},entry));
    return box("moov",box("trak",box("mdia",box("minf",box("stbl",stsd)))));
  }
  @Test public void recognizesStructuredAacOnly() throws Exception {
    assertTrue(SaavnCodecProbe.isAacMp4(fixture(2)));
    assertFalse(SaavnCodecProbe.isAacMp4(fixture(8)));
    assertFalse(SaavnCodecProbe.isAacMp4("junkmp4aesdsjunk".getBytes(StandardCharsets.UTF_8)));
    byte[] cut=fixture(2); assertFalse(SaavnCodecProbe.isAacMp4(java.util.Arrays.copyOf(cut,cut.length-3)));
  }
  @Test public void liveSaavnAacWhenOptedIn() {
    org.junit.Assume.assumeTrue("1".equals(System.getenv("VIBEX_LIVE_PROVIDER_TEST")));
    assertTrue("Live JioSaavn AAC probe failed", SaavnCodecProbe.inspect("NCi0q5GN"));
  }
}
