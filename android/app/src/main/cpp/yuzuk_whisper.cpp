// Java ↔ whisper.cpp köprüsü. Yalnız bellekteki float dizisini metne çevirir; hiçbir dosyaya ses yazmaz.
#include <jni.h>
#include <android/log.h>
#include <string>
#include <thread>
#include <algorithm>
#include "whisper.h"

#define LOG(...) __android_log_print(ANDROID_LOG_INFO, "YuzukWhisper", __VA_ARGS__)

static std::string json_kacis(const char *s) {
    std::string o;
    for (const char *p = s; *p; ++p) {
        unsigned char c = (unsigned char) *p;
        switch (c) {
            case '"': o += "\\\""; break;
            case '\\': o += "\\\\"; break;
            case '\n': o += "\\n"; break;
            case '\r': o += "\\r"; break;
            case '\t': o += "\\t"; break;
            default:
                if (c < 0x20) { char b[8]; snprintf(b, sizeof b, "\\u%04x", c); o += b; }
                else o += (char) c;
        }
    }
    return o;
}

extern "C" JNIEXPORT jlong JNICALL
Java_app_claudian_yuzuk_YerelWhisper_yukle(JNIEnv *env, jclass, jstring yol) {
    const char *y = env->GetStringUTFChars(yol, nullptr);
    whisper_context_params cp = whisper_context_default_params();
    whisper_context *ctx = whisper_init_from_file_with_params(y, cp);
    env->ReleaseStringUTFChars(yol, y);
    return (jlong) ctx;
}

extern "C" JNIEXPORT void JNICALL
Java_app_claudian_yuzuk_YerelWhisper_birak(JNIEnv *, jclass, jlong ctx) {
    if (ctx) whisper_free((whisper_context *) ctx);
}

// Döndürür: {"dil":"tr","cumleler":[{"bas":0.0,"son":1.2,"metin":"..."}]}  (zamanlar saniye, parçanın başından)
extern "C" JNIEXPORT jstring JNICALL
Java_app_claudian_yuzuk_YerelWhisper_coz(JNIEnv *env, jclass, jlong ctxp, jfloatArray ornekler, jstring dil) {
    auto *ctx = (whisper_context *) ctxp;
    if (!ctx) return env->NewStringUTF("{\"hata\":\"model yok\"}");
    jsize n = env->GetArrayLength(ornekler);
    jfloat *veri = env->GetFloatArrayElements(ornekler, nullptr);

    whisper_full_params p = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    std::string d;
    if (dil) { const char *c = env->GetStringUTFChars(dil, nullptr); d = c; env->ReleaseStringUTFChars(dil, c); }
    p.language = d.empty() ? "auto" : d.c_str();
    p.n_threads = std::max(2, std::min(6, (int) std::thread::hardware_concurrency() - 2));
    p.print_progress = false;
    p.print_realtime = false;
    p.print_timestamps = false;
    p.print_special = false;
    p.no_context = true;       // parçalar bağımsız: önceki parçanın metni yanlış yönlendirmesin
    p.suppress_blank = true;

    int r = whisper_full(ctx, p, veri, n);
    env->ReleaseFloatArrayElements(ornekler, veri, JNI_ABORT);
    if (r != 0) return env->NewStringUTF("{\"hata\":\"yazıya dökülemedi\"}");

    std::string o = "{\"dil\":\"";
    int dil_no = whisper_full_lang_id(ctx);
    o += dil_no >= 0 ? whisper_lang_str(dil_no) : "";
    o += "\",\"cumleler\":[";
    int k = whisper_full_n_segments(ctx);
    bool ilk = true;
    for (int i = 0; i < k; ++i) {
        const char *m = whisper_full_get_segment_text(ctx, i);
        std::string metin = m ? m : "";
        size_t a = metin.find_first_not_of(' ');
        if (a == std::string::npos) continue;
        metin = metin.substr(a);
        char zaman[64];
        snprintf(zaman, sizeof zaman, "{\"bas\":%.2f,\"son\":%.2f,\"metin\":\"",
                 whisper_full_get_segment_t0(ctx, i) / 100.0, whisper_full_get_segment_t1(ctx, i) / 100.0);
        if (!ilk) o += ",";
        ilk = false;
        o += zaman;
        o += json_kacis(metin.c_str());
        o += "\"}";
    }
    o += "]}";
    return env->NewStringUTF(o.c_str());
}
