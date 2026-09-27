#include <jni.h>

JNIEXPORT jint JNICALL
Java_ai_drfx_maximus_matrixai_NativeBridge_nativeAbiMarker(JNIEnv *env, jobject thiz) {
    (void) env;
    (void) thiz;
#if defined(__aarch64__)
    return 64;
#else
    return 0;
#endif
}
