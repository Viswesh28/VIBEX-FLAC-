// A container probe is availability evidence, not a claim about what plays.
export function availableCodecLabel(result) {
  if (result?.status === "checking") return "Checking codec…";
  if (result?.status === "saavn_confirmed" && result.codec === "aac") return "AAC";
  if (result?.status !== "container_confirmed" || result.codec !== "flac") return "Codec unverified";
  const details = [];
  if (Number.isFinite(result.bitDepth) && result.bitDepth > 0) details.push(`${result.bitDepth}-bit`);
  if (Number.isFinite(result.sampleRate) && result.sampleRate > 0) details.push(`${result.sampleRate / 1000} kHz`);
  return `FLAC available${details.length ? ` · ${details.join(" / ")}` : ""}${result.aacAvailable ? " · AAC" : ""}`;
}
export function playbackCodecLabel(mime, sampleRate) {
  const name = {"audio/flac":"FLAC", "audio/mpeg":"MP3", "audio/mp4a-latm":"AAC", "audio/aac":"AAC", "audio/opus":"Opus", "audio/vorbis":"Vorbis", "audio/alac":"ALAC", "audio/ac3":"AC-3", "audio/eac3":"E-AC-3", "audio/raw":"PCM"}[mime];
  const rate = Number.isFinite(sampleRate) && sampleRate > 0 ? ` · ${sampleRate / 1000} kHz` : "";
  return name ? `Playing ${name}${rate}` : "";
}

export function downloadedCodecLabel(result, requested, state) {
  if (state !== 3) return requested === "FLAC" ? "FLAC requested" : "Codec unverified";
  if (result?.status === "container_confirmed" && result.codec === "flac") {
    return availableCodecLabel(result).replace("FLAC available", "FLAC");
  }
  if (result?.status === "container_confirmed" && result.codec === "aac") return "AAC";
  return "Codec unverified";
}

export function networkSize(bytes) {
  const n = Number.isFinite(bytes) ? Math.max(0, bytes) : 0;
  return n >= 1048576 ? `${(n / 1048576).toFixed(1)} MB` : `${(n / 1024).toFixed(1)} KB`;
}
