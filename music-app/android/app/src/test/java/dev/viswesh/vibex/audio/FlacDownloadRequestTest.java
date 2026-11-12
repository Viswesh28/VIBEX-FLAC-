package dev.viswesh.vibex.audio;
import static org.junit.Assert.*;
import android.net.Uri;
import androidx.media3.exoplayer.offline.DownloadRequest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public class FlacDownloadRequestTest {
  @Test public void flacDownloadKeepsFormatAndUrlSpecificKey() throws Exception {
    String url="https://tracks.monochrome.st/example?token=abc";
    String key="song|flac|"+VibeMedia.sha256(url);
    DownloadRequest request = new DownloadRequest.Builder("song", Uri.parse(url))
        .setCustomCacheKey(key).setMimeType("audio/flac").build();
    assertEquals("audio/flac", request.mimeType);
    assertEquals(key, request.customCacheKey);
    assertNotEquals(key, "song|flac|"+VibeMedia.sha256(url+"2"));
    assertFalse(key.contains("token"));
    assertEquals(url, request.uri.toString());
  }
}
