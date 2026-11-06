package com.operit.ttssaver;

/**
 * 统一日志出口。自己包一层，避免和 android.util.Log 的类名冲突。
 */
public final class L {
    private static final String TAG = "DoubaoTtsSaver";

    public static void i(String m) {
        try {
            android.util.Log.i(TAG, m);
        } catch (Throwable ignored) {
        }
    }

    public static void w(String m, Throwable t) {
        try {
            android.util.Log.w(TAG, m, t);
        } catch (Throwable ignored) {
        }
    }

    public static void e(String m, Throwable t) {
        try {
            android.util.Log.e(TAG, m, t);
        } catch (Throwable ignored) {
        }
    }
}