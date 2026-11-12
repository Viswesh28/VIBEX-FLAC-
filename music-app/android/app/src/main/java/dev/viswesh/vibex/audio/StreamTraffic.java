package dev.viswesh.vibex.audio;
import java.util.*;

/** Counts bytes delivered over playback HTTP only; excludes downloads and provider queries. */
final class StreamTraffic {
  private static final long WINDOW_MS = 5000;
  private static final class Entry { long total; final ArrayDeque<long[]> samples = new ArrayDeque<>(); }
  private final Map<String,Entry> tracks = new HashMap<>();
  synchronized void reset(String id) { if (id != null && !id.isEmpty()) { if (tracks.size() > 100) tracks.clear(); tracks.put(id, new Entry()); } }
  synchronized void add(String id, int bytes, long now) {
    if (id == null || id.isEmpty() || bytes <= 0) return;
    Entry e = tracks.computeIfAbsent(id, unused -> new Entry()); e.total += bytes;
    long bucket = (now / 1000) * 1000;
    long[] last = e.samples.peekLast();
    if (last != null && last[0] == bucket) last[1] += bytes;
    else e.samples.addLast(new long[]{bucket, bytes});
    trim(e, now);
  }
  private void trim(Entry e,long now) { while (!e.samples.isEmpty() && now - e.samples.peekFirst()[0] >= WINDOW_MS) e.samples.removeFirst(); }
  synchronized long[] report(String id, long now) {
    Entry e = tracks.get(id); if (e == null) return new long[]{0,0};
    trim(e, now); long n=0; for(long[] sample:e.samples)n+=sample[1];
    return new long[]{e.total, n * 1000 / WINDOW_MS};
  }
}
