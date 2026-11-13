#include <jni.h>
#include <string>
#include "whisper.h"

// Runs one offline transcription. Loads the model, transcribes, frees everything.
// Returns UTF-8 bytes, or null if the model could not be loaded or run.
extern "C" JNIEXPORT jbyteArray JNICALL
Java_app_nightkeyboard_Voice_run(JNIEnv *env, jclass, jstring jmodel, jfloatArray jsamples, jint threads) {
    const char *path = env->GetStringUTFChars(jmodel, nullptr);
    whisper_context_params cp = whisper_context_default_params();
    cp.use_gpu = false;
    whisper_context *ctx = whisper_init_from_file_with_params(path, cp);
    env->ReleaseStringUTFChars(jmodel, path);
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
    whisper_free(ctx);
    if (rc != 0) return nullptr;
    jbyteArray arr = env->NewByteArray((jsize) out.size());
    env->SetByteArrayRegion(arr, 0, (jsize) out.size(), (const jbyte *) out.data());
    return arr;
}
