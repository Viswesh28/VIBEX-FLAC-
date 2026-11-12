import test from 'node:test';
import assert from 'node:assert/strict';
import { availableCodecLabel, playbackCodecLabel, downloadedCodecLabel, networkSize } from '../src/v2/codec-label.js';
test('availability is truthful and includes verified FLAC streaminfo', () => {
  assert.equal(availableCodecLabel({status:'container_confirmed',codec:'flac',bitDepth:24,sampleRate:44100}), 'FLAC available · 24-bit / 44.1 kHz');
  assert.equal(availableCodecLabel({status:'container_confirmed',codec:'flac',aacAvailable:true}), 'FLAC available · AAC');
  assert.equal(availableCodecLabel({status:'unknown'}), 'Codec unverified');
  assert.equal(availableCodecLabel({status:'unverified',codec:'flac'}), 'Codec unverified');
  assert.equal(availableCodecLabel({status:'checking'}), 'Checking codec…');
  assert.equal(availableCodecLabel({status:'saavn_confirmed',codec:'aac'}), 'AAC');
});
test('actual playing codec is separate from availability', () => {
  assert.equal(playbackCodecLabel('audio/flac'), 'Playing FLAC');
  assert.equal(playbackCodecLabel('audio/flac', 44100), 'Playing FLAC · 44.1 kHz');
  assert.equal(playbackCodecLabel('audio/mp4a-latm'), 'Playing AAC');
  assert.equal(playbackCodecLabel('audio/unknown'), '');
});

test('downloaded codec comes from offline bytes, not the requested format', () => {
  assert.equal(downloadedCodecLabel(null, 'FLAC', 3), 'Codec unverified');
  assert.equal(downloadedCodecLabel(null, 'FLAC', 2), 'FLAC requested');
  assert.equal(downloadedCodecLabel({status:'container_confirmed',codec:'aac'}, 'FLAC', 3), 'AAC');
  assert.equal(downloadedCodecLabel({status:'container_confirmed',codec:'flac',bitDepth:24,sampleRate:44100}, 'FLAC', 3), 'FLAC · 24-bit / 44.1 kHz');
});

test('audio network units display bytes and rates truthfully', () => {
  assert.equal(networkSize(0), '0.0 KB');
  assert.equal(networkSize(1048576), '1.0 MB');
  assert.equal(networkSize(-1), '0.0 KB');
});
