#include <jni.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <unistd.h>
#include <android/log.h>

#if defined(__aarch64__)
#include "kp/supercall.h"
#endif

#define KP_LOG_TAG "SovaSU_KP"
#define KP_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, KP_LOG_TAG, __VA_ARGS__)

JNIEXPORT jboolean JNICALL
Java_anhiutangerinee_kittisu_KernelPatchNatives_nativeReady(
    JNIEnv *env, jobject thiz, jstring super_key_jstr) {
#if defined(__aarch64__)
    const char *key = super_key_jstr ? (*env)->GetStringUTFChars(env, super_key_jstr, NULL) : NULL;
    const char *effective = (key && key[0] != '\0') ? key : "su";
    bool ready = sc_ready(effective);
    if (key) (*env)->ReleaseStringUTFChars(env, super_key_jstr, key);
    return ready ? JNI_TRUE : JNI_FALSE;
#else
    (void)env; (void)thiz; (void)super_key_jstr;
    return JNI_FALSE;
#endif
}

JNIEXPORT jlong JNICALL
Java_anhiutangerinee_kittisu_KernelPatchNatives_nativeKernelPatchVersion(
    JNIEnv *env, jobject thiz, jstring super_key_jstr) {
#if defined(__aarch64__)
    if (!super_key_jstr) return 0;
    const char *key = (*env)->GetStringUTFChars(env, super_key_jstr, NULL);
    const char *effective = (key && key[0] != '\0') ? key : "su";
    uint32_t ver = sc_kp_ver(effective);
    if (key) (*env)->ReleaseStringUTFChars(env, super_key_jstr, key);
    return (jlong)ver;
#else
    (void)env; (void)thiz; (void)super_key_jstr;
    return 0;
#endif
}

JNIEXPORT jlong JNICALL
Java_anhiutangerinee_kittisu_KernelPatchNatives_nativeSu(
    JNIEnv *env, jobject thiz, jstring super_key_jstr, jint to_uid, jstring sctx_jstr) {
#if defined(__aarch64__)
    if (!super_key_jstr) return -1;
    const char *key = (*env)->GetStringUTFChars(env, super_key_jstr, NULL);
    const char *sctx = sctx_jstr ? (*env)->GetStringUTFChars(env, sctx_jstr, NULL) : NULL;
    struct su_profile profile;
    memset(&profile, 0, sizeof(profile));
    profile.uid = getuid();
    profile.to_uid = (uid_t)to_uid;
    if (sctx && sctx[0] != '\0') {
        strncpy(profile.scontext, sctx, sizeof(profile.scontext) - 1);
    }
    long rc = sc_su((key && key[0] != '\0') ? key : "su", &profile);
    if (sctx) (*env)->ReleaseStringUTFChars(env, sctx_jstr, sctx);
    if (key) (*env)->ReleaseStringUTFChars(env, super_key_jstr, key);
    return (jlong)rc;
#else
    (void)env; (void)thiz; (void)super_key_jstr; (void)to_uid; (void)sctx_jstr;
    return -1;
#endif
}

JNIEXPORT jintArray JNICALL
Java_anhiutangerinee_kittisu_KernelPatchNatives_nativeSuUids(
    JNIEnv *env, jobject thiz, jstring super_key_jstr) {
#if defined(__aarch64__)
    if (!super_key_jstr) return (*env)->NewIntArray(env, 0);
    const char *key = (*env)->GetStringUTFChars(env, super_key_jstr, NULL);
    const char *effective = (key && key[0] != '\0') ? key : "su";
    long num = sc_su_uid_nums(effective);
    if (num <= 0 || num > 4096) {
        if (key) (*env)->ReleaseStringUTFChars(env, super_key_jstr, key);
        return (*env)->NewIntArray(env, 0);
    }
    uid_t *uids = (uid_t *)calloc((size_t)num, sizeof(uid_t));
    if (!uids) {
        if (key) (*env)->ReleaseStringUTFChars(env, super_key_jstr, key);
        return (*env)->NewIntArray(env, 0);
    }
    long n = sc_su_allow_uids(effective, uids, (int)num);
    if (key) (*env)->ReleaseStringUTFChars(env, super_key_jstr, key);
    if (n > 0) {
        jintArray arr = (*env)->NewIntArray(env, (jsize)n);
        (*env)->SetIntArrayRegion(env, arr, 0, (jsize)n, (const jint *)uids);
        free(uids);
        return arr;
    }
    free(uids);
    return (*env)->NewIntArray(env, 0);
#else
    (void)thiz; (void)super_key_jstr;
    return (*env)->NewIntArray(env, 0);
#endif
}

JNIEXPORT jlong JNICALL
Java_anhiutangerinee_kittisu_KernelPatchNatives_nativeGrantSu(
    JNIEnv *env, jobject thiz, jstring super_key_jstr, jint uid, jint to_uid, jstring sctx_jstr) {
#if defined(__aarch64__)
    if (!super_key_jstr) return -1;
    const char *key = (*env)->GetStringUTFChars(env, super_key_jstr, NULL);
    const char *sctx = sctx_jstr ? (*env)->GetStringUTFChars(env, sctx_jstr, NULL) : NULL;
    struct su_profile profile;
    memset(&profile, 0, sizeof(profile));
    profile.uid = (uid_t)uid;
    profile.to_uid = (uid_t)to_uid;
    if (sctx && sctx[0] != '\0') {
        strncpy(profile.scontext, sctx, sizeof(profile.scontext) - 1);
    }
    long rc = sc_su_grant_uid((key && key[0] != '\0') ? key : "su", &profile);
    if (sctx) (*env)->ReleaseStringUTFChars(env, sctx_jstr, sctx);
    if (key) (*env)->ReleaseStringUTFChars(env, super_key_jstr, key);
    return (jlong)rc;
#else
    (void)env; (void)thiz; (void)super_key_jstr; (void)uid; (void)to_uid; (void)sctx_jstr;
    return -1;
#endif
}

JNIEXPORT jlong JNICALL
Java_anhiutangerinee_kittisu_KernelPatchNatives_nativeRevokeSu(
    JNIEnv *env, jobject thiz, jstring super_key_jstr, jint uid) {
#if defined(__aarch64__)
    if (!super_key_jstr) return -1;
    const char *key = (*env)->GetStringUTFChars(env, super_key_jstr, NULL);
    long rc = sc_su_revoke_uid((key && key[0] != '\0') ? key : "su", (uid_t)uid);
    if (key) (*env)->ReleaseStringUTFChars(env, super_key_jstr, key);
    return (jlong)rc;
#else
    (void)env; (void)thiz; (void)super_key_jstr; (void)uid;
    return -1;
#endif
}

JNIEXPORT jint JNICALL
Java_anhiutangerinee_kittisu_KernelPatchNatives_nativeSetUidExclude(
    JNIEnv *env, jobject thiz, jstring super_key_jstr, jint uid, jint exclude) {
#if defined(__aarch64__)
    if (!super_key_jstr) return -1;
    const char *key = (*env)->GetStringUTFChars(env, super_key_jstr, NULL);
    int rc = (int)sc_set_ap_mod_exclude((key && key[0] != '\0') ? key : "su", (uid_t)uid, (int)exclude);
    if (key) (*env)->ReleaseStringUTFChars(env, super_key_jstr, key);
    return rc;
#else
    (void)env; (void)thiz; (void)super_key_jstr; (void)uid; (void)exclude;
    return -1;
#endif
}

JNIEXPORT jint JNICALL
Java_anhiutangerinee_kittisu_KernelPatchNatives_nativeGetUidExclude(
    JNIEnv *env, jobject thiz, jstring super_key_jstr, jint uid) {
#if defined(__aarch64__)
    if (!super_key_jstr) return 0;
    const char *key = (*env)->GetStringUTFChars(env, super_key_jstr, NULL);
    int rc = sc_get_ap_mod_exclude((key && key[0] != '\0') ? key : "su", (uid_t)uid);
    if (key) (*env)->ReleaseStringUTFChars(env, super_key_jstr, key);
    return rc;
#else
    (void)env; (void)thiz; (void)super_key_jstr; (void)uid;
    return 0;
#endif
}

JNIEXPORT jboolean JNICALL
Java_anhiutangerinee_kittisu_KernelPatchNatives_nativeIsSafeMode(
    JNIEnv *env, jobject thiz, jstring super_key_jstr) {
#if defined(__aarch64__)
    if (!super_key_jstr) return JNI_FALSE;
    const char *key = (*env)->GetStringUTFChars(env, super_key_jstr, NULL);
    long rc = sc_su_get_safemode((key && key[0] != '\0') ? key : "su");
    if (key) (*env)->ReleaseStringUTFChars(env, super_key_jstr, key);
    return (rc == 1) ? JNI_TRUE : JNI_FALSE;
#else
    (void)env; (void)thiz; (void)super_key_jstr;
    return JNI_FALSE;
#endif
}

JNIEXPORT jstring JNICALL
Java_anhiutangerinee_kittisu_KernelPatchNatives_nativeSuPath(
    JNIEnv *env, jobject thiz, jstring super_key_jstr) {
#if defined(__aarch64__)
    if (!super_key_jstr) return (*env)->NewStringUTF(env, "");
    const char *key = (*env)->GetStringUTFChars(env, super_key_jstr, NULL);
    char buf[SU_PATH_MAX_LEN] = {0};
    sc_su_get_path((key && key[0] != '\0') ? key : "su", buf, sizeof(buf));
    if (key) (*env)->ReleaseStringUTFChars(env, super_key_jstr, key);
    return (*env)->NewStringUTF(env, buf);
#else
    (void)thiz; (void)super_key_jstr;
    return (*env)->NewStringUTF(env, "");
#endif
}

JNIEXPORT jboolean JNICALL
Java_anhiutangerinee_kittisu_KernelPatchNatives_nativeResetSuPath(
    JNIEnv *env, jobject thiz, jstring super_key_jstr, jstring path_jstr) {
#if defined(__aarch64__)
    if (!super_key_jstr || !path_jstr) return JNI_FALSE;
    const char *key = (*env)->GetStringUTFChars(env, super_key_jstr, NULL);
    const char *path = (*env)->GetStringUTFChars(env, path_jstr, NULL);
    long rc = sc_su_reset_path((key && key[0] != '\0') ? key : "su", path);
    (*env)->ReleaseStringUTFChars(env, path_jstr, path);
    (*env)->ReleaseStringUTFChars(env, super_key_jstr, key);
    return (rc == 0) ? JNI_TRUE : JNI_FALSE;
#else
    (void)env; (void)thiz; (void)super_key_jstr; (void)path_jstr;
    return JNI_FALSE;
#endif
}

JNIEXPORT jlong JNICALL
Java_anhiutangerinee_kittisu_KernelPatchNatives_nativeLoadKernelPatchModule(
    JNIEnv *env, jobject thiz, jstring super_key_jstr, jstring path_jstr, jstring args_jstr) {
#if defined(__aarch64__)
    if (!super_key_jstr || !path_jstr) return -1;
    const char *key = (*env)->GetStringUTFChars(env, super_key_jstr, NULL);
    const char *path = (*env)->GetStringUTFChars(env, path_jstr, NULL);
    const char *args = args_jstr ? (*env)->GetStringUTFChars(env, args_jstr, NULL) : NULL;
    long rc = sc_kpm_load((key && key[0] != '\0') ? key : "su", path, args ? args : "", NULL);
    if (args) (*env)->ReleaseStringUTFChars(env, args_jstr, args);
    (*env)->ReleaseStringUTFChars(env, path_jstr, path);
    (*env)->ReleaseStringUTFChars(env, super_key_jstr, key);
    return (jlong)rc;
#else
    (void)env; (void)thiz; (void)super_key_jstr; (void)path_jstr; (void)args_jstr;
    return -1;
#endif
}

JNIEXPORT jlong JNICALL
Java_anhiutangerinee_kittisu_KernelPatchNatives_nativeUnloadKernelPatchModule(
    JNIEnv *env, jobject thiz, jstring super_key_jstr, jstring name_jstr) {
#if defined(__aarch64__)
    if (!super_key_jstr || !name_jstr) return -1;
    const char *key = (*env)->GetStringUTFChars(env, super_key_jstr, NULL);
    const char *name = (*env)->GetStringUTFChars(env, name_jstr, NULL);
    long rc = sc_kpm_unload((key && key[0] != '\0') ? key : "su", name, NULL);
    (*env)->ReleaseStringUTFChars(env, name_jstr, name);
    (*env)->ReleaseStringUTFChars(env, super_key_jstr, key);
    return (jlong)rc;
#else
    (void)env; (void)thiz; (void)super_key_jstr; (void)name_jstr;
    return -1;
#endif
}

JNIEXPORT jstring JNICALL
Java_anhiutangerinee_kittisu_KernelPatchNatives_nativeControlKernelPatchModule(
    JNIEnv *env, jobject thiz, jstring super_key_jstr, jstring name_jstr, jstring args_jstr) {
#if defined(__aarch64__)
    if (!super_key_jstr || !name_jstr || !args_jstr) return (*env)->NewStringUTF(env, "-1\n");
    const char *key = (*env)->GetStringUTFChars(env, super_key_jstr, NULL);
    const char *name = (*env)->GetStringUTFChars(env, name_jstr, NULL);
    const char *args = (*env)->GetStringUTFChars(env, args_jstr, NULL);
    char out_buf[4096] = {0};
    long rc = sc_kpm_control((key && key[0] != '\0') ? key : "su", name, args, out_buf, sizeof(out_buf) - 1);
    (*env)->ReleaseStringUTFChars(env, args_jstr, args);
    (*env)->ReleaseStringUTFChars(env, name_jstr, name);
    (*env)->ReleaseStringUTFChars(env, super_key_jstr, key);
    char combined[4160] = {0};
    snprintf(combined, sizeof(combined) - 1, "%ld\n%s", rc, out_buf);
    return (*env)->NewStringUTF(env, combined);
#else
    (void)thiz; (void)super_key_jstr; (void)name_jstr; (void)args_jstr;
    return (*env)->NewStringUTF(env, "-1\n");
#endif
}

JNIEXPORT jstring JNICALL
Java_anhiutangerinee_kittisu_KernelPatchNatives_nativeKernelPatchModuleList(
    JNIEnv *env, jobject thiz, jstring super_key_jstr) {
#if defined(__aarch64__)
    if (!super_key_jstr) return (*env)->NewStringUTF(env, "");
    const char *key = (*env)->GetStringUTFChars(env, super_key_jstr, NULL);
    char buf[4096] = {0};
    sc_kpm_list((key && key[0] != '\0') ? key : "su", buf, sizeof(buf) - 1);
    (*env)->ReleaseStringUTFChars(env, super_key_jstr, key);
    return (*env)->NewStringUTF(env, buf);
#else
    (void)thiz; (void)super_key_jstr;
    return (*env)->NewStringUTF(env, "");
#endif
}

JNIEXPORT jstring JNICALL
Java_anhiutangerinee_kittisu_KernelPatchNatives_nativeKernelPatchModuleInfo(
    JNIEnv *env, jobject thiz, jstring super_key_jstr, jstring name_jstr) {
#if defined(__aarch64__)
    if (!super_key_jstr || !name_jstr) return (*env)->NewStringUTF(env, "");
    const char *key = (*env)->GetStringUTFChars(env, super_key_jstr, NULL);
    const char *name = (*env)->GetStringUTFChars(env, name_jstr, NULL);
    char buf[2048] = {0};
    sc_kpm_info((key && key[0] != '\0') ? key : "su", name, buf, sizeof(buf) - 1);
    (*env)->ReleaseStringUTFChars(env, name_jstr, name);
    (*env)->ReleaseStringUTFChars(env, super_key_jstr, key);
    return (*env)->NewStringUTF(env, buf);
#else
    (void)thiz; (void)super_key_jstr; (void)name_jstr;
    return (*env)->NewStringUTF(env, "");
#endif
}
