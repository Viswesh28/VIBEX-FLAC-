import { useEffect, useRef, useState } from 'react';
import { Audio, native } from './native.js';
import { availableCodecLabel } from './codec-label.js';

// Two global workers, shared by all screens. No URLs are stored in the WebView.
const cache = new Map(), pending = new Map(), queue = [];
let running = 0;
function keyOf(s) { return JSON.stringify([s.id, s.name, s.duration, s.artists?.primary]); }
function pump() {
  while (running < 2 && queue.length) {
    const job = queue.shift();
    if (!job.listeners.size) { pending.delete(job.key); continue; }
    running++;
    Audio.inspectLossless({ song: job.song }).catch(() => ({ status: 'unknown' })).then((result) => {
      if (cache.size > 200) cache.clear();
      cache.set(job.key, { result, at: Date.now() });
      for (const fn of job.listeners) fn(result);
    }).finally(() => { running--; pending.delete(job.key); pump(); });
  }
}
function subscribe(song, notify) {
  const key = keyOf(song), hit = cache.get(key);
  if (hit && Date.now() - hit.at < 120000) { notify(hit.result); return () => {}; }
  let job = pending.get(key);
  if (!job) { job = {key, song, listeners: new Set()}; pending.set(key, job); queue.push(job); }
  job.listeners.add(notify); notify({status:'checking'}); pump();
  return () => { job.listeners.delete(notify); };
}
export function CodecAvailability({ song }) {
  const ref = useRef(null), [result, setResult] = useState({status:'unknown'});
  useEffect(() => {
    let off = () => {}, started = false;
    setResult({status:'unknown'});
    if (!native || !song?.id || song.local) return;
    const begin = () => { if (!started && !document.hidden) { started = true; off = subscribe(song, setResult); } };
    let inView = false;
    const observer = typeof IntersectionObserver === 'function' ? new IntersectionObserver((entries) => {
      inView = entries.some((e) => e.isIntersecting); if (inView) begin();
    }, {rootMargin:'80px'}) : null;
    if (observer && ref.current) observer.observe(ref.current); else begin();
    const visible = () => { if (inView || !observer) begin(); };
    document.addEventListener('visibilitychange', visible);
    return () => { off(); observer?.disconnect(); document.removeEventListener('visibilitychange', visible); };
  }, [song]);
  return <span ref={ref} className="codec-tag" title="Available stream verified separately from actual playback; unverified includes missing metadata or temporary failure.">{availableCodecLabel(result)}</span>;
}
