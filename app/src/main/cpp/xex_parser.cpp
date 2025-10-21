#include "xex_parser.h"
#include <android/log.h>
#include <cstring>
#include <sstream>
#include <iomanip>

#define LOG_TAG "XexParser"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

XexParser::XexParser() : valid(false) {
    memset(&execInfo, 0, sizeof(XexExecutionInfo));
}

XexParser::~XexParser() {
}

uint32_t XexParser::readUInt32BE(const uint8_t* data) {
    return ((uint32_t)data[0] << 24) |
           ((uint32_t)data[1] << 16) |
           ((uint32_t)data[2] << 8) |
           (uint32_t)data[3];
}

bool XexParser::parse(const uint8_t* xexData, size_t size) {
    if (size < 24) {
        LOGE("XEX file too small: %zu bytes", size);
        return false;
    }
    
    // Verificar magic "XEX2"
    if (xexData[0] != 'X' || xexData[1] != 'E' || xexData[2] != 'X' || xexData[3] != '2') {
        LOGE("Invalid XEX magic: %c%c%c%c", xexData[0], xexData[1], xexData[2], xexData[3]);
        return false;
    }
    
    LOGD("Valid XEX2 header found");
    
    // Ler offset do certificado (offset 16, big-endian)
    uint32_t certOffset = readUInt32BE(&xexData[16]);
    
    // Ler número de optional headers (offset 20, big-endian)
    uint32_t optHeaderCount = readUInt32BE(&xexData[20]);
    
    LOGD("Certificate offset: 0x%X, Optional headers: %u", certOffset, optHeaderCount);
    
    // Procurar por ExecutionInfo (signature: 0x00040006)
    const uint8_t execInfoSig[4] = {0x00, 0x04, 0x00, 0x06};
    
    uint32_t pos = 24; // Headers começam no offset 24
    
    for (uint32_t i = 0; i < optHeaderCount && pos + 8 <= size; i++) {
        // Ler signature (4 bytes, big-endian)
        uint32_t signature = readUInt32BE(&xexData[pos]);
        uint32_t dataOffset = readUInt32BE(&xexData[pos + 4]);
        
        // Verificar se é ExecutionInfo
        if (signature == 0x00040006) {
            LOGD("Found ExecutionInfo at offset 0x%X", dataOffset);
            
            // ExecutionInfo está em dataOffset
            if (dataOffset + 20 > size) {
                LOGE("ExecutionInfo offset out of bounds");
                return false;
            }
            
            // Ler ExecutionInfo (big-endian)
            memcpy(execInfo.mediaId, &xexData[dataOffset], 4);
            execInfo.version = readUInt32BE(&xexData[dataOffset + 4]);
            execInfo.baseVersion = readUInt32BE(&xexData[dataOffset + 8]);
            memcpy(execInfo.titleId, &xexData[dataOffset + 12], 4);
            execInfo.platform = xexData[dataOffset + 16];
            execInfo.executableType = xexData[dataOffset + 17];
            execInfo.discNumber = xexData[dataOffset + 18];
            execInfo.discCount = xexData[dataOffset + 19];
            
            LOGD("Title ID: %02X%02X%02X%02X",
                 execInfo.titleId[0], execInfo.titleId[1],
                 execInfo.titleId[2], execInfo.titleId[3]);
            
            LOGD("Media ID: %02X%02X%02X%02X",
                 execInfo.mediaId[0], execInfo.mediaId[1],
                 execInfo.mediaId[2], execInfo.mediaId[3]);
            
            valid = true;
            return true;
        }
        
        pos += 8; // Próximo header
    }
    
    LOGE("ExecutionInfo not found in XEX headers");
    return false;
}

std::string XexParser::getTitleIdString() const {
    std::stringstream ss;
    ss << std::hex << std::uppercase << std::setfill('0');
    
    for (int i = 0; i < 4; i++) {
        ss << std::setw(2) << (int)execInfo.titleId[i];
    }
    
    return ss.str();
}

std::string XexParser::getMediaIdString() const {
    std::stringstream ss;
    ss << std::hex << std::uppercase << std::setfill('0');
    
    for (int i = 0; i < 4; i++) {
        ss << std::setw(2) << (int)execInfo.mediaId[i];
    }
    
    return ss.str();
}
