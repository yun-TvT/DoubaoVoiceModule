package com.operit.ttssaver;

import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * DoubaoHook v10 —— 实时去重 + 按句自动保存。
 *
 * ===== 前九版失败的原因，最终定位到「重复写入」 =====
 *
 * 实测数据（G 路 2139 个 960 字节包）逐包比对，发现固定规律：
 *
 *     U D D D D D D D D   U D D D D D D D D   U ...
 *     └─ 1 个新包 ──┘    └─ 8 个完全相同的拷贝 ─┘
 *
 * 周期正好 9。block0 与 block1..block8 逐字节差异 = 0.0，
 * 88.9% 的包与前一个包完全相同。
 *
 * 把每个包都写进文件，等于同一段音频重复 9 遍：
 *   - 语速慢 9 倍
 *   - 9 份相同波形在时间轴叠加，产生梳状滤波 → 金属/电子杂音
 *
 * 去重后用真实音频验证：
 *   1026720 样本 → 114240 样本 = 4.76s @24000Hz 单声道
 *   速率核对：447805 B/s ÷ 9 = 49756 B/s ≈ 24000Hz×2B ✓
 *
 * ===== 本版做法 =====
 * 1. 只取 g([B) 一路（实测 g 与 h 是同一句话的相同内容，取一路避免重复）
 * 2. 每包与「上一个已写入的包」逐字节比对，相同则丢弃
 * 3. 句子边界用豆包自己的语义回调（onPlayComplete / f(TtsPlayEndType)），
 *    不再靠音量阈值猜——去重后静音被压缩了，靠音量判断会失灵
 * 4. 3 秒无新包也强制收尾，保证任何时候文件都是可播放的完整 WAV
 */
public class DoubaoHook implements IXposedHookLoadPackage {

    private static final String PKG = "com.larus.nova";
    private static final String CLS_TTS_LISTENER = "com.larus.audio.call.play.e";

    /** 实测参数：去重后频谱与速率都指向 24000Hz 单声道 16bit。 */
    private static final int SAMPLE_RATE = 24000;
    private static final int CHANNELS = 1;

    /** 多久没有新数据就强制收尾（毫秒）。 */
    private static final long IDLE_CLOSE_MS = 3000;

    private static volatile boolean inited = false;
    private static volatile File outDir;
    private static volatile File logFile;

    // 当前段的写入状态
    private static WavWriter current = null;
    private static File currentFile = null;
    private static byte[] lastWritten = null;
    private static long lastWriteTs = 0;
    private static int segNo = 0;
    private static long segSamples = 0;

    // 统计
    private static long pktG = 0, pktH = 0, dropped = 0, written = 0;

    private static synchronized void log(String msg) {
        L.i(msg);
        try {
            if (logFile == null) logFile = new File("/sdcard/Download/DoubaoTTS_debug.log");
            String ts = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date());
            FileWriter fw = new FileWriter(logFile, true);
            fw.write(ts + "  " + msg + "\n");
            fw.close();
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if (!PKG.equals(lp.packageName)) return;
        synchronized (DoubaoHook.class) {
            if (inited) return;
            inited = true;
        }
        log("=== v10 注入 pid=" + android.os.Process.myPid() + " ===");

        String[] cands = {"/sdcard/Download/DoubaoTTS", "/sdcard/DoubaoTTS"};
        for (String path : cands) {
            File d = new File(path);
            if (!d.exists() && !d.mkdirs()) continue;
            try {
                File probe = new File(d, ".probe");
                FileWriter fw = new FileWriter(probe, false);
                fw.write("ok");
                fw.close();
                probe.delete();
                outDir = d;
                break;
            } catch (Throwable ignored) {
            }
        }
        if (outDir == null) outDir = new File("/sdcard/Download/DoubaoTTS");
        log("输出目录 " + outDir.getAbsolutePath() + " 参数 " + SAMPLE_RATE + "Hz/" + CHANNELS + "ch");

        hookPlayE(lp);
        startWatchdog();
    }

    private void hookPlayE(XC_LoadPackage.LoadPackageParam lp) {
        Class<?> c;
        try {
            c = XposedHelpers.findClass(CLS_TTS_LISTENER, lp.classLoader);
        } catch (Throwable t) {
            log("找不到 " + CLS_TTS_LISTENER + ": " + t);
            return;
        }
        for (final Method m : c.getDeclaredMethods()) {
            final String n = m.getName();
            Class<?>[] pt = m.getParameterTypes();

            // ---- 数据通路：g([B) / h([B) ----
            if (pt.length == 1 && pt[0] == byte[].class) {
                if (!"g".equals(n) && !"h".equals(n)) continue;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam p) {
                        try {
                            byte[] b = (byte[]) p.args[0];
                            if (b == null || b.length == 0) return;
                            if ("g".equals(n)) {
                                onG(b);
                            } else {
                                pktH++;
                            }
                        } catch (Throwable t) {
                            L.w("onG err", t);
                        }
                    }
                });
                log("  挂钩 play.e." + n + "([B)");
                continue;
            }

            // ---- 句子边界：onPlayComplete() / f(TtsPlayEndType) ----
            boolean isEnd = "onPlayComplete".equals(n);
            if (!isEnd && pt.length == 1) {
                String ptn = pt[0].getName();
                if (ptn.contains("TtsPlayEnd") || ptn.contains("PlayEnd")) isEnd = true;
            }
            if (isEnd) {
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam p) {
                        try {
                            closeSegment("句末回调 " + n);
                        } catch (Throwable ignored) {
                        }
                    }
                });
                log("  挂钩 play.e." + n + " 句末");
            }
        }
    }

    /** g 路数据：先做重复包过滤，再写盘。跑在豆包音频线程，只做内存比较+写文件。 */
    private static synchronized void onG(byte[] b) {
        pktG++;

        // 核心：与前一个已写入的包逐字节相同则丢弃（豆包每个包重复发 9 次）
        // 用复用缓冲区避免在音频线程上频繁分配内存
        if (lastWritten != null && lastWritten.length == b.length
                && Arrays.equals(lastWritten, b)) {
            dropped++;
            return;
        }

        if (current == null) openSegment();

        if (current != null) {
            current.write(b, 0, b.length);
            segSamples += b.length / (2 * CHANNELS);
            written++;
            lastWriteTs = System.currentTimeMillis();
            if (lastWritten == null || lastWritten.length != b.length) {
                lastWritten = new byte[b.length];
            }
            System.arraycopy(b, 0, lastWritten, 0, b.length);
        }
    }

    private static void openSegment() {
        segNo++;
        String ts = new SimpleDateFormat("HHmmss", Locale.US).format(new Date());
        File f = new File(outDir, String.format(Locale.US, "%03d_%s.wav", segNo, ts));
        try {
            WavWriter w = new WavWriter(f, SAMPLE_RATE, CHANNELS);
            w.open();
            current = w;
            currentFile = f;
            lastWritten = null;
            segSamples = 0;
            lastWriteTs = System.currentTimeMillis();
            log("开段 " + f.getName());
        } catch (Throwable t) {
            log("开段失败 " + t);
        }
    }

    private static void closeSegment(String why) {
        if (current == null) return;
        long ms = segSamples * 1000L / SAMPLE_RATE;
        WavWriter w = current;
        File f = currentFile;
        current = null;
        currentFile = null;
        lastWritten = null;
        segSamples = 0;
        boolean ok = false;
        try {
            ok = w.finish();
        } catch (Throwable ignored) {
        }
        if (ok) {
            log("收段 " + f.getName() + " " + ms + "ms (" + why + ")");
        } else {
            log("收段(空) 删除 " + f.getName());
            try {
                f.delete();
            } catch (Throwable ignored) {
            }
        }
    }

    /** 3 秒没有新数据就收尾，保证文件随时可播。 */
    private void startWatchdog() {
        Thread th = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        return;
                    }
                    try {
                        synchronized (DoubaoHook.class) {
                            if (current != null && lastWriteTs > 0
                                    && System.currentTimeMillis() - lastWriteTs > IDLE_CLOSE_MS) {
                                closeSegment("静默超时");
                            }
                            if (pktG > 0 && pktG % 3000 == 0) {
                                log("统计 g=" + pktG + " h=" + pktH
                                        + " 已写=" + written + " 去重丢弃=" + dropped);
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        }, "doubao-watchdog");
        th.setDaemon(true);
        th.start();
        log("看门狗已启动");
    }
}