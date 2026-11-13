#include <jni.h>
#include <string>
#include "whisper.h"

// Loads the model. Returns an opaque handle, or 0 on failure.
extern "C" JNIEXPORT jlong JNICALL
Java_app_nightkeyboard_Voice_load(JNIEnv *env, jclass, jstring jmodel) {
    const char *path = env->GetStringUTFChars(jmodel, nullptr);
    whisper_context_params cp = whisper_context_default_params();
    cp.use_gpu = false;
    cp.flash_attn = true;
    whisper_context *ctx = whisper_init_from_file_with_params(path, cp);
    env->ReleaseStringUTFChars(jmodel, path);
    return (jlong) ctx;
}

extern "C" JNIEXPORT void JNICALL
Java_app_nightkeyboard_Voice_free(JNIEnv *, jclass, jlong h) {
    if (h != 0) whisper_free((whisper_context *) h);
}

// Transcribes with a loaded model. Returns UTF-8 bytes, or null on failure.
extern "C" JNIEXPORT jbyteArray JNICALL
Java_app_nightkeyboard_Voice_run(JNIEnv *env, jclass, jlong h, jfloatArray jsamples, jint threads) {
    whisper_context *ctx = (whisper_context *) h;
    if (ctx == nullptr) return nullptr;
    jsize n = env->GetArrayLength(jsamples);
    jfloat *s = env->GetFloatArrayElements(jsamples, nullptr);
    whisper_full_params p = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    p.print_progress = false;
    p.print_realtime = false;
    p.print_special = false;
    p.print_timestamps = false;
    p.translate = false;
    p.language = "en";
    p.n_threads = threads;
    p.no_context = true;
    // Short clips: shrink the encoder window from 30 s to what is needed (much faster).
    if (n <= 16000 * 28) {
        int ac = n / 320 + 100;
        if (ac < 300) ac = 300;
        if (ac > 1500) ac = 1500;
        p.audio_ctx = ac;
    }
    int rc = whisper_full(ctx, p, s, n);
    env->ReleaseFloatArrayElements(jsamples, s, JNI_ABORT);
    std::string out;
    if (rc == 0) {
        int segs = whisper_full_n_segments(ctx);
        for (int i = 0; i < segs; i++) {
            const char *t = whisper_full_get_segment_text(ctx, i);
            if (t != nullptr) out += t;
        }
    }
    if (rc != 0) return nullptr;
    jbyteArray arr = env->NewByteArray((jsize) out.size());
    env->SetByteArrayRegion(arr, 0, (jsize) out.size(), (const jbyte *) out.data());
    return arr;
}
