package dev.viswesh.vibex.audio;
import static org.junit.Assert.*;
import org.junit.Test;
public class StreamTrafficTest {
  @Test public void countsBytesSeparatelyAndExpiresRateNotTotal() {
    StreamTraffic t=new StreamTraffic(); t.reset("a"); t.add("a",10000,1000); t.add("b",5000,1000);
    assertArrayEquals(new long[]{10000,2000},t.report("a",2000));
    assertArrayEquals(new long[]{10000,0},t.report("a",6000));
    assertArrayEquals(new long[]{5000,1000},t.report("b",2000));
    t.reset("a"); assertArrayEquals(new long[]{0,0},t.report("a",2000));
  }
}
