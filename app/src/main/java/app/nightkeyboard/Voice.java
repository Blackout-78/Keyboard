package app.nightkeyboard;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Offline voice input: records the mic in memory, transcribes with whisper.cpp on the phone. */
final class Voice {
    private static final int RATE = 16000;
    private static final int MAX = RATE * 180;
    private static boolean lib;

    static {
        try {
            System.loadLibrary("nightvoice");
            lib = true;
        } catch (Throwable t) {
            lib = false;
        }
    }

    private Voice() {}

    private static native long load(String model);
    private static native void free(long ctx);
    private static native byte[] run(long ctx, float[] samples, int threads);

    private static long ctx;
    private static Thread loader;

    /** Loads the model in the background while you speak, so it is ready when you stop. */
    static synchronized void preload(final String model) {
        if (!lib || ctx != 0 || (loader != null && loader.isAlive())) return;
        loader = new Thread(new Runnable() {
            @Override public void run() {
                long h = load(model);
                synchronized (Voice.class) { ctx = h; }
            }
        });
        loader.start();
    }

    private static void waitLoader() {
        Thread t;
        synchronized (Voice.class) { t = loader; }
        try { if (t != null) t.join(); } catch (InterruptedException x) { /* ignore */ }
    }

    /** Frees the model from memory. */
    static void release() {
        waitLoader();
        long h;
        synchronized (Voice.class) { h = ctx; ctx = 0; loader = null; }
        if (h != 0) free(h);
    }

    private static AudioRecord rec;
    private static Thread th;
    private static volatile boolean going;
    private static short[] buf;
    private static int len;

    static boolean available() { return lib; }

    static synchronized boolean start() {
        if (going) return true;
        try {
            int min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (min <= 0) return false;
            final AudioRecord r = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(min, RATE));
            if (r.getState() != AudioRecord.STATE_INITIALIZED) {
                r.release();
                return false;
            }
            buf = new short[MAX];
            len = 0;
            rec = r;
            going = true;
            r.startRecording();
            th = new Thread(new Runnable() {
                @Override public void run() {
                    short[] chunk = new short[1600];
                    while (going) {
                        int n = r.read(chunk, 0, chunk.length);
                        if (n <= 0) break;
                        synchronized (Voice.class) {
                            if (buf == null) break;
                            int c = Math.min(n, MAX - len);
                            System.arraycopy(chunk, 0, buf, len, c);
                            len += c;
                            if (len >= MAX) break;
                        }
                    }
                }
            });
            th.start();
            return true;
        } catch (Throwable t) {
            stop();
            return false;
        }
    }

    /** Stops recording and returns the audio. The in-memory copy is wiped. */
    static float[] stop() {
        AudioRecord r;
        Thread t;
        synchronized (Voice.class) {
            r = rec;
            t = th;
            going = false;
            rec = null;
            th = null;
        }
        if (r != null) {
            try { r.stop(); } catch (Throwable x) { /* ignore */ }
            try { if (t != null) t.join(1000); } catch (InterruptedException x) { /* ignore */ }
            r.release();
        }
        short[] b;
        int n;
        synchronized (Voice.class) {
            b = buf;
            n = len;
            buf = null;
            len = 0;
        }
        if (b == null) return new float[0];
        float[] f = new float[n];
        for (int i = 0; i < n; i++) f[i] = b[i] / 32768f;
        Arrays.fill(b, (short) 0);
        return f;
    }

    static void cancel() {
        float[] f = stop();
        Arrays.fill(f, 0f);
        new Thread(new Runnable() { @Override public void run() { release(); } }).start();
    }

    /** Returns the text, "" if nothing was heard, or null if the model failed. Wipes the samples. */
    static String transcribe(String model, float[] samples) {
        try {
            if (!lib) return null;
            if (samples.length < RATE / 3) return "";
            int threads = Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors()));
            waitLoader();
            long h;
            synchronized (Voice.class) { h = ctx; }
            if (h == 0) {
                h = load(model);
                synchronized (Voice.class) { ctx = h; }
            }
            if (h == 0) return null;
            byte[] out = run(h, samples, threads);
            release();
            if (out == null) return null;
            String t = new String(out, StandardCharsets.UTF_8).replaceAll("\\s+", " ").trim();
            if (t.matches("^[\\[(].*[\\])]$")) return "";
            return t;
        } catch (Throwable x) {
            release();
            return null;
        } finally {
            Arrays.fill(samples, 0f);
        }
    }
}
