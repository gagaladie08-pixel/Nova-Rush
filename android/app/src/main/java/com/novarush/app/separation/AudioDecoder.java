package com.novarush.app.separation;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Décode un fichier audio (MP3, M4A, WAV…) en float stéréo entrelacé. */
final class AudioDecoder {
    private AudioDecoder() {}

    static final class Result {
        final int sampleRate;
        final long frames;

        Result(int sampleRate, long frames) {
            this.sampleRate = sampleRate;
            this.frames = frames;
        }
    }

    interface Progress {
        boolean onProgress(double fraction);
    }

    static Result decode(File in, File out, Progress progress) throws Exception {
        MediaExtractor ex = new MediaExtractor();
        MediaCodec codec = null;
        try (RawAudio.Writer w = new RawAudio.Writer(out, 2)) {
            ex.setDataSource(in.getAbsolutePath());
            int track = -1;
            MediaFormat fmt = null;
            for (int i = 0; i < ex.getTrackCount(); i++) {
                MediaFormat f = ex.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    track = i;
                    fmt = f;
                    break;
                }
            }
            if (track < 0) throw new IOException("aucune piste audio");
            ex.selectTrack(track);
            long durationUs = fmt.containsKey(MediaFormat.KEY_DURATION) ? fmt.getLong(MediaFormat.KEY_DURATION) : 0;
            int rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int channels = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            boolean isFloat = false;
            codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME));
            codec.configure(fmt, null, null, 0);
            codec.start();
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false;
            boolean outputDone = false;
            long lastReport = 0;
            while (!outputDone) {
                if (!inputDone) {
                    int ii = codec.dequeueInputBuffer(10000);
                    if (ii >= 0) {
                        ByteBuffer ib = codec.getInputBuffer(ii);
                        int size = ib == null ? -1 : ex.readSampleData(ib, 0);
                        if (size < 0) {
                            codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            codec.queueInputBuffer(ii, 0, size, ex.getSampleTime(), 0);
                            ex.advance();
                        }
                    }
                }
                int oi = codec.dequeueOutputBuffer(info, 10000);
                if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat of = codec.getOutputFormat();
                    rate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    channels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    isFloat = of.containsKey(MediaFormat.KEY_PCM_ENCODING)
                            && of.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT;
                } else if (oi >= 0) {
                    ByteBuffer ob = codec.getOutputBuffer(oi);
                    if (ob != null && info.size > 0) {
                        ob.position(info.offset);
                        ob.limit(info.offset + info.size);
                        ob.order(ByteOrder.LITTLE_ENDIAN);
                        int bytesPer = isFloat ? 4 : 2;
                        int frames = info.size / (bytesPer * channels);
                        for (int f = 0; f < frames; f++) {
                            float l = 0, r = 0;
                            for (int c = 0; c < channels; c++) {
                                float v = isFloat ? ob.getFloat() : ob.getShort() / 32768f;
                                if (c == 0) l = v;
                                else if (c == 1) r = v;
                            }
                            if (channels == 1) r = l;
                            w.put(l);
                            w.put(r);
                        }
                        w.addFrames(frames);
                    }
                    codec.releaseOutputBuffer(oi, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true;
                    if (durationUs > 0 && info.presentationTimeUs - lastReport > 2_000_000) {
                        lastReport = info.presentationTimeUs;
                        if (progress != null && !progress.onProgress(Math.min(1, (double) info.presentationTimeUs / durationUs))) {
                            throw new UvrSeparator.Cancelled();
                        }
                    }
                }
            }
            if (w.frames == 0) throw new IOException("aucun échantillon décodé");
            return new Result(rate, w.frames);
        } finally {
            if (codec != null) {
                try {
                    codec.stop();
                } catch (Exception ignored) {
                    // déjà arrêté
                }
                codec.release();
            }
            ex.release();
        }
    }
}
