#include <jni.h>

#include <android/log.h>

#include <string>
#include <vector>

#include "socket_client.hpp"

#define LOG_TAG "PLNA_NetJNI"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static plna::net::SocketClient g_socketClient;
static JavaVM* g_jvm = nullptr;
static jclass g_nativeNetworkClass = nullptr;

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    (void)reserved;
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

void dispatchToKotlin(int type, const std::vector<uint8_t>& body) {
    if (!g_nativeNetworkClass) return;

    JNIEnv* env = nullptr;
    if (g_jvm->AttachCurrentThread(&env, nullptr) == JNI_OK) {
        jmethodID method = env->GetStaticMethodID(g_nativeNetworkClass, "onDataReceived", "(I[B)V");
        if (method) {
            jbyteArray jData = env->NewByteArray(static_cast<jsize>(body.size()));
            if (jData) {
                env->SetByteArrayRegion(
                        jData,
                        0,
                        static_cast<jsize>(body.size()),
                        reinterpret_cast<const jbyte*>(body.data())
                );
                env->CallStaticVoidMethod(g_nativeNetworkClass, method, static_cast<jint>(type), jData);
                env->DeleteLocalRef(jData);
            }
        } else {
            LOGE("Failed to find static method onDataReceived");
        }
        g_jvm->DetachCurrentThread();
    }
}

void dispatchStatusToKotlin(bool connected) {
    if (!g_nativeNetworkClass) return;

    JNIEnv* env = nullptr;
    if (g_jvm->AttachCurrentThread(&env, nullptr) == JNI_OK) {
        jmethodID method = env->GetStaticMethodID(
                g_nativeNetworkClass,
                "onConnectionStatusChanged",
                "(Z)V"
        );
        if (method) {
            env->CallStaticVoidMethod(
                    g_nativeNetworkClass,
                    method,
                    static_cast<jboolean>(connected)
            );
        } else {
            LOGE("Failed to find static method onConnectionStatusChanged");
        }
        g_jvm->DetachCurrentThread();
    }
}

extern "C" JNIEXPORT void JNICALL
Java_by_smartplanogram_plnanote_data_remote_NativeNetwork_nativeInit(JNIEnv* env, jclass clazz) {
    if (g_nativeNetworkClass) {
        env->DeleteGlobalRef(g_nativeNetworkClass);
    }
    g_nativeNetworkClass = reinterpret_cast<jclass>(env->NewGlobalRef(clazz));

    g_socketClient.setOnStatusChanged([](bool connected) {
        dispatchStatusToKotlin(connected);
    });

    g_socketClient.setOnPacketReceived(
            [](plna::net::PacketType type, const std::vector<uint8_t>& body) {
                dispatchToKotlin(static_cast<int>(type), body);
            }
    );
}

extern "C" JNIEXPORT jboolean JNICALL
Java_by_smartplanogram_plnanote_data_remote_NativeNetwork_nativeConnect(
        JNIEnv* env,
        jclass,
        jstring host,
        jint port
) {
    if (!host) return JNI_FALSE;
    const char* nativeHost = env->GetStringUTFChars(host, nullptr);
    bool result = g_socketClient.connect(nativeHost, port);
    env->ReleaseStringUTFChars(host, nativeHost);
    return result ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_by_smartplanogram_plnanote_data_remote_NativeNetwork_nativeDisconnect(JNIEnv* env, jclass) {
    g_socketClient.disconnect();
    if (g_nativeNetworkClass) {
        env->DeleteGlobalRef(g_nativeNetworkClass);
        g_nativeNetworkClass = nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_by_smartplanogram_plnanote_data_remote_NativeNetwork_nativeSend(
        JNIEnv* env,
        jclass,
        jint type,
        jbyteArray data
) {
    std::vector<uint8_t> buffer;
    if (data) {
        jsize len = env->GetArrayLength(data);
        if (len > 0) {
            buffer.resize(static_cast<size_t>(len));
            env->GetByteArrayRegion(data, 0, len, reinterpret_cast<jbyte*>(buffer.data()));
        }
    }
    g_socketClient.sendPacket(static_cast<plna::net::PacketType>(type), buffer);
}

extern "C" JNIEXPORT jlong JNICALL
Java_by_smartplanogram_plnanote_data_remote_NativeNetwork_nativeGetBytesSent(JNIEnv*, jclass) {
    return static_cast<jlong>(g_socketClient.getBytesSent());
}

extern "C" JNIEXPORT jlong JNICALL
Java_by_smartplanogram_plnanote_data_remote_NativeNetwork_nativeGetBytesReceived(JNIEnv*, jclass) {
    return static_cast<jlong>(g_socketClient.getBytesReceived());
}

extern "C" JNIEXPORT void JNICALL
Java_by_smartplanogram_plnanote_data_remote_NativeNetwork_nativeResetStats(JNIEnv*, jclass) {
    g_socketClient.resetStats();
}

extern "C" JNIEXPORT void JNICALL
Java_by_smartplanogram_plnanote_data_remote_NativeNetwork_nativeTriggerHeartbeat(JNIEnv*, jclass) {
    g_socketClient.triggerManualHeartbeat();
}
