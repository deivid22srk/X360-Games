#include "hash_utils.h"
#include <android/log.h>
#include <sstream>
#include <iomanip>

#define LOG_TAG "HashUtils"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

void HashUtils::calculateSHA1(const uint8_t* data, size_t size, uint8_t* hashOut) {
    // TODO: Implementar SHA-1
    // Por enquanto, preenche com zeros
    LOGD("TODO: Calculate SHA-1 for %zu bytes", size);
    
    for (int i = 0; i < 20; i++) {
        hashOut[i] = 0;
    }
}

std::string HashUtils::hashToHexString(const uint8_t* hash, size_t size) {
    std::stringstream ss;
    ss << std::hex << std::setfill('0');
    
    for (size_t i = 0; i < size; i++) {
        ss << std::setw(2) << (int)hash[i];
    }
    
    return ss.str();
}
