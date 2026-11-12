package dev.viswesh.vibex.audio;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.*;
import androidx.activity.result.ActivityResult;
import androidx.media3.common.*;
import androidx.media3.exoplayer.offline.*;
import com.getcapacitor.*;
import com.getcapacitor.annotation.*;
import java.io.*;
import org.json.*;

@CapacitorPlugin(
    name = "VibexAudio",
    permissions = {
      @Permission(
          alias = "notifications",
          strings = {Manifest.permission.POST_NOTIFICATIONS})
    })
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public class VibexAudioPlugin extends Plugin {
  private final java.util.concurrent.ExecutorService io =
      java.util.concurrent.Executors.newFixedThreadPool(3);
  private final java.util.Set<String> downloadPlanning = java.util.concurrent.ConcurrentHashMap.newKeySet();
  // Isolated, bounded worker pool: catalog probing must not starve normal app IO.
  private final java.util.concurrent.ThreadPoolExecutor codecChecks =
      new java.util.concurrent.ThreadPoolExecutor(2, 2, 0L, java.util.concurrent.TimeUnit.MILLISECONDS,
          new java.util.concurrent.ArrayBlockingQueue<>(8));

  private final Runnable visibleObserver =
      () -> {
        VibeAudioService s = VibeAudioService.instance;
        try {
          if (s != null) notifyListeners("playerState", appSnapshot(s, ""));
        } catch (JSONException ignored) {
        }
      };

  private JSObject appSnapshot(VibeAudioService service, String queueToken) throws JSONException {
    JSObject data = JSObject.fromJSONObject(service.snapshot(queueToken));
    if (getActivity() instanceof dev.viswesh.vibex.MainActivity) {
      data.put(
          "returnToPlayer", ((dev.viswesh.vibex.MainActivity) getActivity()).playerReturnToken());
    }
    return data;
  }

  @Override
  protected void handleOnNewIntent(Intent intent) {
    // A notification/widget tap also works while the activity is already visible.
    visibleObserver.run();
  }

  private final Runnable downloadObserver =
      () -> notifyListeners("downloadsChanged", new JSObject());

  @Override
  public void load() {
    VibeAudioService.uiObserver = visibleObserver;
    VibeMedia.downloadObserver = downloadObserver;
  }

  @Override
  protected void handleOnResume() {
    VibeAudioService.uiObserver = visibleObserver;
    VibeMedia.downloadObserver = downloadObserver;
    visibleObserver.run();
    downloadObserver.run();
  }

  @Override
  protected void handleOnPause() {
    if (VibeAudioService.uiObserver == visibleObserver) VibeAudioService.uiObserver = null;
    if (VibeMedia.downloadObserver == downloadObserver) VibeMedia.downloadObserver = null;
  }

  @Override
  protected void handleOnDestroy() {
    if (VibeAudioService.uiObserver == visibleObserver) VibeAudioService.uiObserver = null;
    if (VibeMedia.downloadObserver == downloadObserver) VibeMedia.downloadObserver = null;
    io.shutdownNow();
    codecChecks.shutdownNow();
  }

  private interface ServiceAction {
    void accept(VibeAudioService service) throws Exception;
  }

  private VibeStore store() {
    return VibeStore.get(getContext());
  }

  private void service(PluginCall call, ServiceAction action) {
    getActivity()
        .runOnUiThread(
            () -> {
              try {
                if (VibeAudioService.instance == null)
                  getContext().startService(new Intent(getContext(), VibeAudioService.class));
                waitFor(call, action, 0);
              } catch (Exception e) {
                call.reject("Unable to start player", e);
              }
            });
  }

  private void waitFor(PluginCall call, ServiceAction action, int n) {
    if (VibeAudioService.instance != null) {
      try {
        action.accept(VibeAudioService.instance);
      } catch (Exception e) {
        call.reject(e.getMessage(), e);
      }
    } else if (n < 40)
      new Handler(Looper.getMainLooper()).postDelayed(() -> waitFor(call, action, n + 1), 50);
    else call.reject("Player service startup timed out");
  }

  @PluginMethod
  public void snapshot(PluginCall call) {
    service(call, s -> call.resolve(appSnapshot(s, call.getString("queueToken", ""))));
  }

  @PluginMethod
  public void command(PluginCall call) {
    service(
        call,
        s -> {
          String command = call.getString("action", "");
          if (command.equals("next") || command.equals("previous") || command.equals("select"))
            s.recordSkip();
          s.cancelFade();
          s.cancelRadio();
          switch (command) {
            case "play":
              s.player.play();
              break;
            case "pause":
              s.pause();
              break;
            case "next":
              s.player.seekToNextMediaItem();
              break;
            case "previous":
              s.player.seekToPrevious();
              break;
            case "seek":
              s.player.seekTo((long) (call.getDouble("position", 0.0) * 1000));
              break;
            case "select":
              int i = call.getInt("index", 0);
              if (i >= 0 && i < s.player.getMediaItemCount()) {
                s.player.seekTo(i, 0);
                s.player.play();
              }
              break;
            case "move":
              int from = call.getInt("from", -1), to = call.getInt("to", -1);
              if (from >= 0
                  && to >= 0
                  && from < s.player.getMediaItemCount()
                  && to < s.player.getMediaItemCount()) s.player.moveMediaItem(from, to);
              break;
            case "remove":
              int at = call.getInt("index", -1);
              if (at >= 0 && at < s.player.getMediaItemCount()) s.player.removeMediaItem(at);
              break;
            case "clear":
              MediaItem current = s.player.getCurrentMediaItem();
              long pos = s.player.getCurrentPosition();
              if (current != null) {
                s.player.setMediaItem(current, pos);
                s.player.prepare();
              } else s.player.clearMediaItems();
              break;
            case "sleep":
              s.sleep(call.getInt("minutes", 0));
              break;
            default:
              call.reject("Unknown player action");
              return;
          }
          if (command.equals("move") || command.equals("remove") || command.equals("clear"))
            s.queueChanged();
          s.save();
          call.resolve(appSnapshot(s, ""));
        });
  }

  @PluginMethod
  public void selectCodec(PluginCall call) {
    String codec = call.getString("codec", "");
    if (!codec.equals("flac") && !codec.equals("aac")) { call.reject("Invalid codec"); return; }
    service(call, s -> {
      MediaItem item = s.player.getCurrentMediaItem();
      if (item != null && item.localConfiguration != null && !"vibex".equals(item.localConfiguration.uri.getScheme())) {
        call.reject("Codec selection is unavailable for local files"); return;
      }
      JSONObject prefs = store().settings();
      prefs.put("quality", codec.equals("flac") ? "flac" : "320kbps");
      prefs.put("audioSource", codec.equals("flac") ? "flac" : "saavn");
      store().write("settings", prefs.toString());
      s.apply(prefs);
      if (item != null) {
        long position = s.player.getCurrentPosition();
        int index = s.player.getCurrentMediaItemIndex();
        boolean play = s.player.getPlayWhenReady();
        s.cancelFade();
        VibeMedia media = VibeMedia.get(getContext());
        media.bypassOfflineFor(item.mediaId);
        s.player.stop();
        s.player.seekTo(index, position);
        s.player.prepare();
        s.player.setPlayWhenReady(play);
      }
      call.resolve(appSnapshot(s, ""));
    });
  }

  @PluginMethod
  public void setQueue(PluginCall call) {
    service(
        call,
        s -> {
          s.queue(
              call.getArray("songs", new JSArray()),
              call.getInt("index", 0),
              (long) (call.getDouble("position", 0.0) * 1000),
              call.getBoolean("play", true));
          call.resolve(appSnapshot(s, ""));
        });
  }

  @PluginMethod
  public void enqueue(PluginCall call) {
    service(
        call,
        s -> {
          JSObject song = call.getObject("song");
          if (song == null) {
            call.reject("Missing song");
            return;
          }
          int at =
              call.getBoolean("next", false)
                  ? s.player.getCurrentMediaItemIndex() + 1
                  : s.player.getMediaItemCount();
          s.player.addMediaItem(Math.max(0, at), VibeMedia.get(getContext()).item(song));
          s.queueChanged();
          s.save();
          call.resolve(appSnapshot(s, ""));
        });
  }

  @PluginMethod
  public void settings(PluginCall call) {
    JSObject prefs = call.getObject("settings", new JSObject());
    store().write("settings", prefs.toString());
    service(
        call,
        s -> {
          s.apply(prefs);
          call.resolve();
        });
  }

  @PluginMethod
  public void getDocument(PluginCall call) {
    JSObject r = new JSObject();
    r.put("value", store().read(call.getString("key", "")));
    call.resolve(r);
  }

  @PluginMethod
  public void setDocument(PluginCall call) {
    String key = call.getString("key", ""), value = call.getString("value", "");
    if (value.length() > 20 * 1024 * 1024) {
      call.reject("Library is too large");
      return;
    }
    store().write(key, value);
    // Native song metadata is indexed when queued, downloaded, imported, or explicitly restored.
    // A like, pin, timing tweak, or preference edit must not re-index the entire library.
    call.resolve();
  }

  @PluginMethod
  public void artwork(PluginCall call) {
    io.execute(
        () -> {
          try {
            call.resolve(
                new JSObject()
                    .put("uri", VibeArtwork.cached(getContext(), call.getString("url", ""))));
          } catch (Exception e) {
            call.reject("Artwork unavailable", e);
          }
        });
  }

  @PluginMethod
  public void events(PluginCall call) {
    JSObject r = new JSObject();
    synchronized (store()) {
      String revision = Long.toString(store().eventsRevision());
      r.put("revision", revision);
      if (!revision.equals(call.getString("revision", ""))) r.put("events", store().events());
    }
    call.resolve(r);
  }

  @PluginMethod
  public void restoreLibrary(PluginCall call) {
    try {
      String value = call.getString("library", "");
      if (value.length() > 20 * 1024 * 1024)
        throw new IllegalArgumentException("Library is too large");
      JSONObject library = new JSONObject(value);
      if (library.optInt("version") != 2)
        throw new IllegalArgumentException("Unsupported library version");
      store()
          .restoreLibrary(
              value, library.getJSONObject("settings"), call.getArray("events", new JSArray()));
      service(
          call,
          s -> {
            s.apply(library.getJSONObject("settings"));
            call.resolve();
          });
    } catch (Exception e) {
      call.reject("Backup restore failed", e);
    }
  }

  @PluginMethod
  public void restoreEvents(PluginCall call) {
    service(
        call,
        s -> {
          s.resetAccounting();
          store().replaceEvents(call.getArray("events", new JSArray()));
          call.resolve();
        });
  }

  @PluginMethod
  public void downloads(PluginCall call) {
    service(
        call,
        service -> {
          JSObject r = new JSObject();
          VibeMedia m = VibeMedia.get(getContext());
          r.put("downloads", m.downloadState());
          r.put("waiting", m.manager.getNotMetRequirements() != 0);
          r.put("streamBytes", m.stream.getCacheSpace());
          r.put("downloadBytes", m.downloads.getCacheSpace());
          call.resolve(r);
        });
  }

  @PluginMethod
  public void inspectDownloadedCodec(PluginCall call) {
    String id = call.getString("id", "");
    if (id.isEmpty() || id.length() > 512) { call.reject("Invalid download id"); return; }
    io.execute(() -> {
      try { call.resolve(JSObject.fromJSONObject(VibeMedia.get(getContext()).inspectOfflineCodec(id))); }
      catch (Exception e) { call.resolve(new JSObject().put("status", "unknown")); }
    });
  }

  @PluginMethod
  public void download(PluginCall call) {
    service(
        call,
        service -> {
          try {
            String action = call.getString("action", "add"), id = call.getString("id", "");
            Context c = getContext();
            if (action.equals("add")) {
              JSObject song = call.getObject("song");
              if (song == null || song.optBoolean("local")) {
                call.reject("Only online tracks can be downloaded");
                return;
              }
              String songId = song.getString("id");
              store().song(song);
              VibeMedia media = VibeMedia.get(c);
              Download existing = media.manager.getDownloadIndex().getDownload(songId);
              if (existing != null && existing.state == Download.STATE_REMOVING) {
                call.reject("Please wait for this download to be removed before downloading again.");
                return;
              }
              Format fmt = service.player.getAudioFormat();
              boolean currentlyFlac = service.player.getCurrentMediaItem() != null
                  && songId.equals(service.player.getCurrentMediaItem().mediaId)
                  && fmt != null && "audio/flac".equals(fmt.sampleMimeType);
              String dlQuality = store().settings().optString("downloadQuality", "160kbps");
              String mode = store().settings().optString("audioSource", "auto");
              boolean requireFlac = currentlyFlac || "flac".equals(dlQuality);
              if (existing != null) {
                if (requireFlac && !"audio/flac".equals(existing.request.mimeType)) {
                  call.reject("A lower-quality copy already exists. Remove it from Downloads, then download again for FLAC.");
                  return;
                }
                // Preserve the exact request and cache key on retries; never mix formats.
                DownloadService.sendAddDownload(c, VibeDownloadService.class, existing.request, false);
                call.resolve();
                return;
              }
              final String pinned = media.flacRecording(songId);
              if (!downloadPlanning.add(songId)) {
                call.reject("This download is already being prepared.");
                return;
              }
              io.execute(() -> {
                try {
                  LosslessProvider.Result flac = null;
                  if (requireFlac || "auto".equals(mode) || "flac".equals(mode))
                    flac = pinned != null ? LosslessProvider.resolvePinned(pinned) : LosslessProvider.resolve(song);
                  boolean verified = flac != null && flac.getEvidence().getVerification()
                      == LosslessProvider.Verification.CONTAINER_CONFIRMED;
                  if (!verified && requireFlac) {
                    call.reject("FLAC download unavailable or could not be verified. Please retry; no smaller copy was downloaded.");
                    return;
                  }
                  DownloadRequest request;
                  if (verified) {
                    // Freeze this exact URL for all retries. If it expires, fail rather than
                    // concatenate a different recording to a partial download.
                    String url = flac.getUrl();
                    request = new DownloadRequest.Builder(songId, Uri.parse(url))
                        .setCustomCacheKey(songId + "|flac|" + VibeMedia.sha256(url))
                        .setMimeType("audio/flac").build();
                  } else {
                    String q = dlQuality.matches("(48|96|160|320)kbps") ? dlQuality : "160kbps";
                    request = new DownloadRequest.Builder(songId,
                        Uri.parse("vibex://song/" + Uri.encode(songId) + "?q=" + q + "&dl=1"))
                        .setCustomCacheKey(songId + "|" + q).setMimeType("audio/mp4").build();
                  }
                  DownloadService.sendAddDownload(c, VibeDownloadService.class, request, false);
                  call.resolve();
                } catch (Exception e) { call.reject("Download request failed", e); }
                finally { downloadPlanning.remove(songId); }
              });
              return;
            } else if (action.equals("remove"))
              DownloadService.sendRemoveDownload(c, VibeDownloadService.class, id, false);
            else if (action.equals("pause"))
              DownloadService.sendSetStopReason(c, VibeDownloadService.class, id, 1, false);
            else if (action.equals("resume"))
              DownloadService.sendSetStopReason(c, VibeDownloadService.class, id, 0, false);
            else {
              call.reject("Unknown download action");
              return;
            }
            call.resolve();
          } catch (Exception e) {
            call.reject("Download request failed", e);
          }
        });
  }

  @PluginMethod
  public void clearCache(PluginCall call) {
    service(
        call,
        service -> {
          VibeMedia media = VibeMedia.get(getContext());
          io.execute(
              () -> {
                try {
                  media.clearStream();
                  call.resolve();
                } catch (Exception e) {
                  call.reject("Cache could not be cleared", e);
                }
              });
        });
  }

  /** Probe availability, not playback. Never expose stream URLs to the WebView. */
  @PluginMethod
  public void inspectLossless(PluginCall call) {
    JSObject song = call.getObject("song");
    if (song == null || song.toString().length() > 65536) {
      call.reject("Invalid song metadata");
      return;
    }
    try {
      codecChecks.execute(() -> {
        LosslessProvider.Result result = LosslessProvider.resolve(song);
        JSObject out = new JSObject();
        boolean aacAvailable = SaavnCodecProbe.inspect(song.optString("id"));
        out.put("aacAvailable", aacAvailable);
        if (result != null && result.getEvidence().getVerification()
            == LosslessProvider.Verification.CONTAINER_CONFIRMED) {
          LosslessProvider.Evidence evidence = result.getEvidence();
          out.put("status", "container_confirmed");
          out.put("codec", evidence.getCodec());
          out.put("sampleRate", evidence.getSampleRate());
          out.put("bitDepth", evidence.getBitDepth());
        } else if (aacAvailable) {
          out.put("status", "saavn_confirmed");
          out.put("codec", "aac");
        } else {
          // Inability to verify either provider is not evidence of unavailability.
          out.put("status", "unknown");
        }
        call.resolve(out);
      });
    } catch (java.util.concurrent.RejectedExecutionException e) {
      call.resolve(new JSObject().put("status", "unknown"));
    }
  }

  @PluginMethod
  public void pickAudio(PluginCall call) {
    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
    i.setType("audio/*");
    i.addCategory(Intent.CATEGORY_OPENABLE);
    i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
    i.addFlags(
        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
    startActivityForResult(call, i, "pickedAudio");
  }

  @ActivityCallback
  private void pickedAudio(PluginCall call, ActivityResult result) {
    if (call == null) return;
    if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
      call.resolve(new JSObject().put("songs", new JSArray()));
      return;
    }
    Intent data = result.getData();
    io.execute(
        () -> {
          JSONArray songs = new JSONArray();
          int count =
              data.getClipData() == null ? 1 : Math.min(100, data.getClipData().getItemCount());
          for (int n = 0; n < count; n++) {
            Uri uri =
                data.getClipData() == null
                    ? data.getData()
                    : data.getClipData().getItemAt(n).getUri();
            if (uri == null) continue;
            try {
              getContext()
                  .getContentResolver()
                  .takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
              MediaMetadataRetriever r = new MediaMetadataRetriever();
              try {
                r.setDataSource(getContext(), uri);
                JSONObject s = new JSONObject();
                s.put("id", "local:" + uri.toString());
                String title = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                s.put("name", title == null ? "Local audio" : title);
                s.put("subtitle", r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST));
                String dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                s.put("duration", dur == null ? 0 : Long.parseLong(dur) / 1000);
                s.put("local", true);
                s.put("localUri", uri.toString());
                String art =
                    VibeArtwork.local(getContext(), uri.toString(), r.getEmbeddedPicture());
                s.put(
                    "image",
                    art.isEmpty()
                        ? new JSONArray()
                        : new JSONArray().put(new JSONObject().put("url", art)));
                store().song(s);
                songs.put(s);
              } finally {
                r.release();
              }
            } catch (Exception ignored) {
            }
          }
          JSObject o = new JSObject();
          o.put("songs", songs);
          o.put("skipped", count - songs.length());
          call.resolve(o);
        });
  }

  @PluginMethod
  public void exportBackup(PluginCall call) {
    Intent i =
        new Intent(Intent.ACTION_CREATE_DOCUMENT)
            .setType("application/json")
            .addCategory(Intent.CATEGORY_OPENABLE)
            .putExtra(Intent.EXTRA_TITLE, "VIBEX-backup.json");
    startActivityForResult(call, i, "backupDestination");
  }

  @ActivityCallback
  private void backupDestination(PluginCall call, ActivityResult result) {
    if (call == null) return;
    if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
      call.resolve(new JSObject().put("cancelled", true));
      return;
    }
    try (OutputStream out =
        getContext().getContentResolver().openOutputStream(result.getData().getData())) {
      out.write(call.getString("data", "").getBytes(java.nio.charset.StandardCharsets.UTF_8));
      call.resolve();
    } catch (Exception e) {
      call.reject("Backup export failed", e);
    }
  }
}
