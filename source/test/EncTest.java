import de.sciss.jump3r.mp3.*;
import de.sciss.jump3r.mpg.*;
import java.io.*;

/**
 * 验证 jump3r 的完整初始化链 + 编码结果是否有效。
 */
public class EncTest {

    static Lame lame;
    static LameGlobalFlags flags;

    static void initModules() {
        lame = new Lame();
        BitStream bs = new BitStream();
        GainAnalysis ga = new GainAnalysis();
        Presets p = new Presets();
        QuantizePVT qupvt = new QuantizePVT();
        Quantize qu = new Quantize();
        VBRTag vbr = new VBRTag();
        Version ver = new Version();
        ID3Tag id3 = new ID3Tag();
        Reservoir rv = new Reservoir();
        Takehiro tak = new Takehiro();
        PsyModel psy = new PsyModel();
        MPGLib mpg = new MPGLib();
        Interface intf = new Interface();
        Common common = new Common();
        Parse parse = new Parse();
        GetAudio gaud = new GetAudio();

        lame.setModules(ga, bs, p, qupvt, qu, vbr, ver, id3, mpg);
        bs.setModules(ga, mpg, ver, vbr);
        id3.setModules(bs, ver);
        p.setModules(lame);
        qu.setModules(bs, rv, qupvt, tak);
        qupvt.setModules(tak, rv, psy);
        rv.setModules(bs);
        tak.setModules(qupvt);
        vbr.setModules(lame, bs, ver);
        gaud.setModules(parse, mpg);
        parse.setModules(ver, id3, p);
        mpg.setModules(intf, common);
        intf.setModules(vbr, common);
    }

    public static void main(String[] a) throws Exception {
        int sr = 44100, ch = 2, br = 96;
        initModules();
        flags = lame.lame_init();
        flags.num_channels = ch;
        flags.in_samplerate = sr;
        flags.brate = br;
        flags.mode = ch >= 2 ? MPEGMode.STEREO : MPEGMode.MONO;
        flags.quality = 5;
        flags.write_id3tag_automatic = false;
        flags.bWriteVbrTag = false;
        flags.out_samplerate = sr;
        flags.VBR = de.sciss.jump3r.mp3.VbrMode.vbr_off;
        int rc = lame.lame_init_params(flags);
        System.out.println("lame_init_params rc=" + rc);
        if (rc < 0) {
            System.out.println("FAILED");
            return;
        }

        byte[] pcm = readAll(new File(a[0]));
        int hdr = 44;
        int len = pcm.length - hdr;
        int frames = len / (2 * ch);
        int[] L = new int[frames];
        int[] R = new int[frames];
        for (int i = 0; i < frames; i++) {
            int b = hdr + i * 2 * ch;
            int s0 = ((pcm[b] & 0xff) | (pcm[b + 1] << 8));
            L[i] = s0 << 16;
            if (ch >= 2) {
                int s1 = ((pcm[b + 2] & 0xff) | (pcm[b + 3] << 8));
                R[i] = s1 << 16;
            } else {
                R[i] = L[i];
            }
        }

        FileOutputStream fos = new FileOutputStream(a[1]);
        int total = 0;
        // 分块编码，模拟流式场景
        int chunk = 1024;
        byte[] out = new byte[chunk * 5 / 4 + 7200];
        for (int pos = 0; pos < frames; pos += chunk) {
            int n = Math.min(chunk, frames - pos);
            int[] lc = new int[n];
            int[] rc2 = new int[n];
            System.arraycopy(L, pos, lc, 0, n);
            System.arraycopy(R, pos, rc2, 0, n);
            int got = lame.lame_encode_buffer_int(flags, lc, rc2, n, out, 0, out.length);
            if (got > 0) {
                fos.write(out, 0, got);
                total += got;
            }
        }
        byte[] fl = new byte[7200];
        int fn = lame.lame_encode_flush(flags, fl, 0, fl.length);
        if (fn > 0) {
            fos.write(fl, 0, fn);
            total += fn;
        }
        fos.close();
        lame.lame_close(flags);
        System.out.println("total mp3 bytes=" + total);
        System.out.println("file size=" + new File(a[1]).length());
    }

    static byte[] readAll(File f) throws IOException {
        byte[] d = new byte[(int) f.length()];
        DataInputStream in = new DataInputStream(new FileInputStream(f));
        in.readFully(d);
        in.close();
        return d;
    }
}