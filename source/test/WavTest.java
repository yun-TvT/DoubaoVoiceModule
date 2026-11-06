import java.io.*;
import java.nio.file.*;

public class WavTest {
    public static void main(String[] a) throws Exception {
        // 模拟豆包数据流：44100Hz 立体声，4608 字节/包，写入 30 秒
        int sr = 44100, ch = 2;
        File f = new File(a[0]);
        Class<?> wcls = Class.forName("com.operit.ttssaver.WavWriter");

        // 用我们自己的类路径重编译版测试
        com.operit.ttssaver.WavWriter w = new com.operit.ttssaver.WavWriter(f, sr, ch);
        w.open();
        int frameBytes = 4608;
        int totalPcm = 0;
        // 30秒 = 44100*2*2*30 = 5292000 字节
        int target = sr * ch * 2 * 30;
        byte[] chunk = new byte[frameBytes];
        for (int i = 0; i < frameBytes; i++) {
            chunk[i] = (byte) (Math.sin(i * 0.1) * 100);
        }
        while (totalPcm < target) {
            w.write(chunk, 0, frameBytes);
            totalPcm += frameBytes;
        }
        System.out.println("写入 PCM: " + totalPcm + " 字节 (30秒)");
        boolean ok = w.finish();
        System.out.println("finish=" + ok);
        System.out.println("文件大小=" + f.length() + " (应为 44 + " + totalPcm + " = " + (44 + totalPcm) + ")");
        System.out.println("时长ms=" + w.getDurationMs() + " (应为 30000)");
    }
}