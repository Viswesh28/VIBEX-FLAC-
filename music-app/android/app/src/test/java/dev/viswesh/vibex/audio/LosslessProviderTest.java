package dev.viswesh.vibex.audio;

import static org.junit.Assert.*;
import org.junit.Test;
import org.json.JSONObject;

public class LosslessProviderTest {
  @Test public void matchesOnlySameRecordingMetadata() throws Exception {
    JSONObject c = new JSONObject().put("title", "Blinding Lights").put("artist", "The Weeknd").put("duration", 200);
    assertTrue(LosslessProvider.matches("Blinding Lights", "The Weeknd", 201, c));
    assertFalse(LosslessProvider.matches("Blinding Lights", "The Weeknd", 210, c));
    assertFalse(LosslessProvider.matches("Blinding Lights (Live)", "The Weeknd", 200, c));
    assertFalse(LosslessProvider.matches("Blinding Lights", "Someone Else", 200, c));
    assertFalse(LosslessProvider.matches("Blinding Lights", "The Weeknd", 0, c));
  }
  @Test public void flacStreaminfoIsOnlyContainerEvidence() {
    byte[] b = new byte[42];
    b[0]='f'; b[1]='L'; b[2]='a'; b[3]='C'; b[7]=34;
    int rate=44100, bits=24;
    b[18]=(byte)(rate >> 12); b[19]=(byte)(rate >> 4);
    b[20]=(byte)(((rate & 15) << 4) | ((bits-1) >> 4)); b[21]=(byte)((bits-1) << 4);
    LosslessProvider.Evidence e = LosslessProvider.parseFlacHeader(b);
    assertNotNull(e); assertEquals(Integer.valueOf(44100), e.getSampleRate());
    assertEquals(Integer.valueOf(24), e.getBitDepth());
    assertEquals(LosslessProvider.Verification.CONTAINER_CONFIRMED, e.getVerification());
    b[0]=0; assertNull(LosslessProvider.parseFlacHeader(b));
    assertNull(LosslessProvider.parseFlacHeader(new byte[41]));
  }
  @Test public void cacheKeyIncludesCompleteUrlWithoutLeakingIt() throws Exception {
    String a = VibeMedia.sha256("https://example.com/a?token=secret");
    assertEquals(a, VibeMedia.sha256("https://example.com/a?token=secret"));
    assertNotEquals(a, VibeMedia.sha256("https://example.com/b?token=secret"));
    assertFalse(a.contains("secret")); assertEquals(64, a.length());
  }
  @Test public void liveLosslessProviderResolutionWhenOptedIn() throws Exception {
    org.junit.Assume.assumeTrue("Set VIBEX_LIVE_PROVIDER_TEST=1 to run live network test",
        "1".equals(System.getenv("VIBEX_LIVE_PROVIDER_TEST")));
    JSONObject artist = new JSONObject().put("name", "The Weeknd");
    JSONObject song = new JSONObject().put("name", "Blinding Lights").put("duration", 200)
        .put("artists", new JSONObject().put("primary", new org.json.JSONArray().put(artist)));
    LosslessProvider.Result result = LosslessProvider.resolve(song);
    assertNotNull("Live provider resolution failed", result);
    assertEquals(LosslessProvider.Verification.CONTAINER_CONFIRMED, result.getEvidence().getVerification());
    assertEquals("flac", result.getEvidence().getCodec());
  }
}
