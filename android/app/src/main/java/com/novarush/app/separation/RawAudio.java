package com.novarush.app.separation;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.channels.FileChannel;

/** Fichiers audio bruts : float32 little-endian, trames entrelacées. */
final class RawAudio {
    private RawAudio() {}

    /** Écriture séquentielle tamponnée. */
    static final class Writer implements Closeable, Resampler.Sink {
        private final RandomAccessFile file;
        private final FileChannel channel;
        private final ByteBuffer buf;
        private final FloatBuffer fbuf;
        final int channels;
        long frames;

        Writer(File f, int channels) throws IOException {
            if (f.exists() && !f.delete()) throw new IOException("suppression impossible : " + f);
            file = new RandomAccessFile(f, "rw");
            channel = file.getChannel();
            buf = ByteBuffer.allocateDirect(1 << 18).order(ByteOrder.LITTLE_ENDIAN);
            fbuf = buf.asFloatBuffer();
            this.channels = channels;
        }

        void put(float v) throws IOException {
            if (!fbuf.hasRemaining()) flush();
            fbuf.put(v);
        }

        @Override
        public void write(float[] data, int count) throws IOException {
            int n = count * channels;
            for (int i = 0; i < n; i++) put(data[i]);
            frames += count;
        }

        void addFrames(long n) {
            frames += n;
        }

        private void flush() throws IOException {
            buf.position(0);
            buf.limit(fbuf.position() * 4);
            while (buf.hasRemaining()) channel.write(buf);
            buf.clear();
            fbuf.clear();
        }

        @Override
        public void close() throws IOException {
            flush();
            channel.close();
            file.close();
        }
    }

    /** Lecture à accès aléatoire (zéros hors limites). */
    static final class Reader implements Closeable, Resampler.Source {
        private final RandomAccessFile file;
        private final FileChannel channel;
        private final int channels;
        private final long frames;
        private ByteBuffer buf = ByteBuffer.allocateDirect(0);

        Reader(File f, int channels) throws IOException {
            file = new RandomAccessFile(f, "r");
            channel = file.getChannel();
            this.channels = channels;
            frames = channel.size() / (4L * channels);
        }

        @Override
        public long frames() {
            return frames;
        }

        @Override
        public int channels() {
            return channels;
        }

        @Override
        public void read(long start, int count, float[] dst) throws IOException {
            java.util.Arrays.fill(dst, 0, count * channels, 0f);
            long a = Math.max(0, start);
            long b = Math.min(frames, start + count);
            if (b <= a) return;
            int bytes = (int) ((b - a) * channels * 4);
            if (buf.capacity() < bytes) buf = ByteBuffer.allocateDirect(bytes).order(ByteOrder.LITTLE_ENDIAN);
            buf.clear();
            buf.limit(bytes);
            long pos = a * channels * 4L;
            while (buf.hasRemaining()) {
                int r = channel.read(buf, pos + buf.position());
                if (r < 0) break;
            }
            buf.flip();
            buf.order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(dst, (int) (a - start) * channels, (int) (b - a) * channels);
        }

        @Override
        public void close() throws IOException {
            channel.close();
            file.close();
        }
    }
}
