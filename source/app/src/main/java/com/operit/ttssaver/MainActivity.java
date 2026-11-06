package com.operit.ttssaver;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 纯说明页。这个模块本身不需要任何操作——
 * 在 LSPosed 里勾选作用域「豆包」，然后在豆包里语音通话即可自动保存。
 */
public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        float d = getResources().getDisplayMetrics().density;
        int pad = (int) (20 * d);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(Color.parseColor("#0E1621"));

        TextView title = new TextView(this);
        title.setText("豆包语音录制");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        title.setGravity(Gravity.CENTER);
        root.addView(title, lp(-1, -2, 0, (int) (6 * d)));

        TextView sub = new TextView(this);
        sub.setText("v11 · 纯 Hook 方案");
        sub.setTextColor(Color.parseColor("#7F8FA6"));
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        sub.setGravity(Gravity.CENTER);
        root.addView(sub, lp(-1, -2, 0, (int) (22 * d)));

        addBlock(root, "怎么用",
                "1. LSPosed 里启用本模块\n"
                        + "2. 作用域勾选「豆包」\n"
                        + "3. 强制停止豆包后重新打开\n"
                        + "4. 打语音电话，正常聊天\n\n"
                        + "全程无需任何手动操作。", d);

        addBlock(root, "文件位置",
                "/sdcard/Download/DoubaoTTS/\n\n"
                        + "命名格式：001_161356.wav\n"
                        + "（序号_录制时刻）", d);

        addBlock(root, "录制规则",
                "· 只录豆包的声音，不含你的麦克风\n"
                        + "· 豆包说完一段，自动存一个文件\n"
                        + "· 豆包说一长段（比如唱歌）\n"
                        + "  会完整保存在同一个文件里", d);

        addBlock(root, "音频规格",
                "24000Hz · 单声道 · 16bit 无损 WAV\n"
                        + "数字原声逐位记录，未经压缩转码\n"
                        + "约 2.8MB / 分钟", d);

        addBlock(root, "如果没生成文件",
                "① 确认作用域已勾选「豆包」\n"
                        + "② 强制停止豆包后再重开\n"
                        + "③ 查看日志：\n"
                        + "   /sdcard/Download/DoubaoTTS_debug.log", d);

        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);
    }

    private void addBlock(LinearLayout parent, String head, String body, float d) {
        TextView h = new TextView(this);
        h.setText(head);
        h.setTextColor(Color.parseColor("#4FC3F7"));
        h.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        parent.addView(h, lp(-1, -2, 0, (int) (8 * d)));

        TextView t = new TextView(this);
        t.setText(body);
        t.setTextColor(Color.parseColor("#D0D8E0"));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setLineSpacing(6 * d, 1f);
        parent.addView(t, lp(-1, -2, 0, (int) (24 * d)));
    }

    private LinearLayout.LayoutParams lp(int w, int h, int top, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.topMargin = top;
        p.bottomMargin = bottom;
        return p;
    }
}