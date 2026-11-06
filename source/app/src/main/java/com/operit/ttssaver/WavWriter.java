package com.operit.ttssaver;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * 流式写 WAV。解决 v5 "0 秒文件" 的问题：
 *
 * v5 的坑：头部 data 长度是 open() 时写的占位 0，要等 finish() 回填。
 * 但通话中的文件永远等不到 finish() —— 播放器看到长度 0 就显示 0 秒，
 * 用户以为文件是空的。其实数据全在（一个字节没丢）。
 *
 * v6 的解法：写盘线程每收满 64KB PCM 就主动回填一次真实长度。
 * 这样通话进行中文件也随时是"合法 WAV"，随时能播、时长随时正确。
 * 代价是每 64KB 多一次 seek+write，微秒级，可忽略。
 */
public class WavWriter {

    /** 每写多少 PCM 主动回填一次头部长度。 */
    private static final long BACKFILL_EVERY = 64 * 1024;

    private final File outFile;
    private final int sampleRate;
    private final int channels;

    private BufferedOutputStream out;
    private long pcmBytes = 0;
    private long lastBackfill = 0;
    private boolean finished = false;

    public WavWriter(File outFile, int sampleRate, int channels) {
        this.outFile = outFile;
        this.sampleRate = sampleRate;
        this.channels = channels;
    }

    public synchronized void open() throws IOException {
        File parent = outFile.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        out = new BufferedOutputStream(new FileOutputStream(outFile), 256 * 1024);
        out.write(header(0));
        lastBackfill = 0;
    }

    /** 写入 PCM（16-bit little-endian 交错）。 */
    public synchronized void write(byte[] b, int off, int len) {
        if (finished || out == null || len <= 0) return;
        try {
            out.write(b, off, len);
            pcmBytes += len;
            // 每 64KB 回填一次长度，让文件随时处于可播状态
            if (pcmBytes - lastBackfill >= BACKFILL_EVERY) {
                flushLen();
                lastBackfill = pcmBytes;
            }
        } catch (Throwable t) {
            L.w("WavWriter.write failed", t);
        }
    }

    public synchronized void write(byte[] b) {
        if (b != null) write(b, 0, b.length);
    }

    /** 把当前累计长度刷进头部（必须先 flush 数据，保证头部<=数据）。 */
    private void flushLen() {
        try {
            out.flush(); // 让文件真实长度先落地
        } catch (Throwable ignored) {
        }
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(outFile, "rw");
            raf.seek(4);
            raf.writeInt(Integer.reverseBytes((int) (36 + pcmBytes)));
            raf.seek(40);
            raf.writeInt(Integer.reverseBytes((int) pcmBytes));
        } catch (Throwable t) {
            L.w("WavWriter backfill failed", t);
        } finally {
            try {
                if (raf != null) raf.close();
            } catch (Throwable ignored) {
            }
        }
    }

    /** 收尾。返回是否产出了非空文件。 */
    public synchronized boolean finish() {
        if (finished) return pcmBytes > 0;
        finished = true;
        try {
            if (out != null) out.flush();
        } catch (Throwable ignored) {
        }
        try {
            if (out != null) out.close();
        } catch (Throwable ignored) {
        }
        out = null;
        if (pcmBytes > 0) flushLen();
        return pcmBytes > 0;
    }

    public File getFile() {
        return outFile;
    }

    public int getDurationMs() {
        if (sampleRate <= 0 || channels <= 0) return 0;
        long frames = pcmBytes / (2L * channels);
        return (int) (frames * 1000L / sampleRate);
    }

    /** 生成 44 字节标准 WAV 头。 */
    private byte[] header(long dataSize) {
        int byteRate = sampleRate * channels * 2;
        int blockAlign = channels * 2;
        byte[] h = new byte[44];
        h[0] = 'R'; h[1] = 'I'; h[2] = 'F'; h[3] = 'F';
        putLE(h, 4, 36 + dataSize);
        h[8] = 'W'; h[9] = 'A'; h[10] = 'V'; h[11] = 'E';
        h[12] = 'f'; h[13] = 'm'; h[14] = 't'; h[15] = ' ';
        putLE(h, 16, 16);
        putLE(h, 20, 1);
        putLE(h, 22, channels);
        putLE(h, 24, sampleRate);
        putLE(h, 28, byteRate);
        putLE(h, 32, blockAlign);
        putLE(h, 34, 16);
        h[36] = 'd'; h[37] = 'a'; h[38] = 't'; h[39] = 'a';
        putLE(h, 40, dataSize);
        return h;
    }

    private static void putLE(byte[] b, int off, long v) {
        b[off] = (byte) (v & 0xff);
        b[off + 1] = (byte) ((v >> 8) & 0xff);
        b[off + 2] = (byte) ((v >> 16) & 0xff);
        b[off + 3] = (byte) ((v >> 24) & 0xff);
    }
}