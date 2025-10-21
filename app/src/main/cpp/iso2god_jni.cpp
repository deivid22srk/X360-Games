#include <jni.h>
#include <string>
#include <android/log.h>
#include "iso2god_converter.h"

#define LOG_TAG "Iso2God-JNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Referência global ao conversor
static Iso2GodConverter* gConverter = nullptr;

// Helper para converter jstring para std::string
std::string jstringToString(JNIEnv* env, jstring jStr) {
    if (!jStr) return "";
    
    const char* cStr = env->GetStringUTFChars(jStr, nullptr);
    std::string result(cStr);
    env->ReleaseStringUTFChars(jStr, cStr);
    
    return result;
}

// Converter string C++ para jstring
jstring stringToJstring(JNIEnv* env, const std::string& str) {
    return env->NewStringUTF(str.c_str());
}

extern "C" {

JNIEXPORT jint JNICALL
Java_com_x360games_archivedownloader_utils_Iso2GodConverter_nativeConvertIso(
    JNIEnv* env,
    jobject thiz,
    jstring jIsoPath,
    jstring jOutputPath,
    jobject jProgressCallback
) {
    LOGD("nativeConvertIso called");
    
    std::string isoPath = jstringToString(env, jIsoPath);
    std::string outputPath = jstringToString(env, jOutputPath);
    
    LOGD("ISO: %s, Output: %s", isoPath.c_str(), outputPath.c_str());
    
    // Criar conversor se não existir
    if (!gConverter) {
        gConverter = new Iso2GodConverter();
    }
    
    // Obter referência global para o callback
    jobject gCallbackRef = env->NewGlobalRef(jProgressCallback);
    
    // Buscar método onProgress do callback
    jclass callbackClass = env->GetObjectClass(gCallbackRef);
    jmethodID onProgressMethod = env->GetMethodID(
        callbackClass,
        "onProgress",
        "(FLjava/lang/String;)V"
    );
    
    if (!onProgressMethod) {
        LOGE("Failed to find onProgress method");
        env->DeleteGlobalRef(gCallbackRef);
        return -3;
    }
    
    // Criar callback lambda que chama o método Java
    auto progressCallback = [env, gCallbackRef, onProgressMethod](
        float progress,
        const std::string& status
    ) {
        jstring jStatus = stringToJstring(env, status);
        env->CallVoidMethod(
            gCallbackRef,
            onProgressMethod,
            progress,
            jStatus
        );
        env->DeleteLocalRef(jStatus);
    };
    
    // Executar conversão
    int result = gConverter->convertIsoToGod(isoPath, outputPath, progressCallback);
    
    // Liberar referência global
    env->DeleteGlobalRef(gCallbackRef);
    
    LOGD("Conversion result: %d", result);
    return result;
}

JNIEXPORT jobject JNICALL
Java_com_x360games_archivedownloader_utils_Iso2GodConverter_nativeGetIsoInfo(
    JNIEnv* env,
    jobject thiz,
    jstring jIsoPath
) {
    LOGD("nativeGetIsoInfo called");
    
    std::string isoPath = jstringToString(env, jIsoPath);
    
    // Criar conversor se não existir
    if (!gConverter) {
        gConverter = new Iso2GodConverter();
    }
    
    // Obter informações do ISO
    IsoInfo* info = gConverter->getIsoInfo(isoPath);
    if (!info) {
        LOGE("Failed to get ISO info");
        return nullptr;
    }
    
    // Criar objeto IsoInfo em Java
    jclass isoInfoClass = env->FindClass(
        "com/x360games/archivedownloader/utils/IsoInfo"
    );
    
    if (!isoInfoClass) {
        LOGE("Failed to find IsoInfo class");
        delete info;
        return nullptr;
    }
    
    jmethodID constructor = env->GetMethodID(
        isoInfoClass,
        "<init>",
        "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;JLjava/lang/String;)V"
    );
    
    if (!constructor) {
        LOGE("Failed to find IsoInfo constructor");
        delete info;
        return nullptr;
    }
    
    // Converter strings C++ para jstring
    jstring jGameName = stringToJstring(env, info->gameName);
    jstring jTitleId = stringToJstring(env, info->titleId);
    jstring jMediaId = stringToJstring(env, info->mediaId);
    jstring jPlatform = stringToJstring(env, info->platform);
    jstring jVolumeDescriptor = stringToJstring(env, info->volumeDescriptor);
    jlong jSizeBytes = (jlong)info->sizeBytes;
    
    // Criar objeto
    jobject isoInfoObj = env->NewObject(
        isoInfoClass,
        constructor,
        jGameName,
        jTitleId,
        jMediaId,
        jPlatform,
        jSizeBytes,
        jVolumeDescriptor
    );
    
    // Limpar referências locais
    env->DeleteLocalRef(jGameName);
    env->DeleteLocalRef(jTitleId);
    env->DeleteLocalRef(jMediaId);
    env->DeleteLocalRef(jPlatform);
    env->DeleteLocalRef(jVolumeDescriptor);
    
    delete info;
    
    LOGD("ISO info retrieved successfully");
    return isoInfoObj;
}

JNIEXPORT void JNICALL
Java_com_x360games_archivedownloader_utils_Iso2GodConverter_nativeCancelConversion(
    JNIEnv* env,
    jobject thiz
) {
    LOGD("nativeCancelConversion called");
    
    if (gConverter) {
        gConverter->cancelConversion();
    }
}

// Chamado quando a biblioteca é carregada
JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* vm, void* reserved) {
    LOGD("Iso2God native library loaded");
    return JNI_VERSION_1_6;
}

// Chamado quando a biblioteca é descarregada
JNIEXPORT void JNICALL
JNI_OnUnload(JavaVM* vm, void* reserved) {
    LOGD("Iso2God native library unloaded");
    
    if (gConverter) {
        delete gConverter;
        gConverter = nullptr;
    }
}

} // extern "C"
