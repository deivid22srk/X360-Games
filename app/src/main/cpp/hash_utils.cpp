#include "hash_utils.h"
#include <android/log.h>
#include <sstream>
#include <iomanip>
#include <cstring>

#define LOG_TAG "HashUtils"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// Rotação à esquerda de 32 bits
#define ROL32(value, bits) (((value) << (bits)) | ((value) >> (32 - (bits))))

// Implementação SHA-1 conforme RFC 3174
void HashUtils::calculateSHA1(const uint8_t* data, size_t size, uint8_t* hashOut) {
    // Inicialização das constantes H
    uint32_t h0 = 0x67452301;
    uint32_t h1 = 0xEFCDAB89;
    uint32_t h2 = 0x98BADCFE;
    uint32_t h3 = 0x10325476;
    uint32_t h4 = 0xC3D2E1F0;
    
    // Padding
    uint64_t originalSize = size;
    uint64_t paddedSize = ((size + 8) / 64 + 1) * 64;
    uint8_t* paddedData = new uint8_t[paddedSize];
    
    memcpy(paddedData, data, size);
    paddedData[size] = 0x80;
    
    for (size_t i = size + 1; i < paddedSize - 8; i++) {
        paddedData[i] = 0;
    }
    
    // Adicionar tamanho original em bits (big-endian)
    uint64_t bitLength = originalSize * 8;
    for (int i = 0; i < 8; i++) {
        paddedData[paddedSize - 1 - i] = (bitLength >> (i * 8)) & 0xFF;
    }
    
    // Processar cada chunk de 512 bits (64 bytes)
    for (size_t chunk = 0; chunk < paddedSize; chunk += 64) {
        uint32_t w[80];
        
        // Dividir chunk em 16 palavras de 32 bits (big-endian)
        for (int i = 0; i < 16; i++) {
            w[i] = (paddedData[chunk + i * 4] << 24) |
                   (paddedData[chunk + i * 4 + 1] << 16) |
                   (paddedData[chunk + i * 4 + 2] << 8) |
                   (paddedData[chunk + i * 4 + 3]);
        }
        
        // Estender as 16 palavras para 80
        for (int i = 16; i < 80; i++) {
            w[i] = ROL32(w[i-3] ^ w[i-8] ^ w[i-14] ^ w[i-16], 1);
        }
        
        // Inicializar variáveis de trabalho
        uint32_t a = h0;
        uint32_t b = h1;
        uint32_t c = h2;
        uint32_t d = h3;
        uint32_t e = h4;
        
        // Loop principal
        for (int i = 0; i < 80; i++) {
            uint32_t f, k;
            
            if (i < 20) {
                f = (b & c) | ((~b) & d);
                k = 0x5A827999;
            } else if (i < 40) {
                f = b ^ c ^ d;
                k = 0x6ED9EBA1;
            } else if (i < 60) {
                f = (b & c) | (b & d) | (c & d);
                k = 0x8F1BBCDC;
            } else {
                f = b ^ c ^ d;
                k = 0xCA62C1D6;
            }
            
            uint32_t temp = ROL32(a, 5) + f + e + k + w[i];
            e = d;
            d = c;
            c = ROL32(b, 30);
            b = a;
            a = temp;
        }
        
        // Adicionar ao hash
        h0 += a;
        h1 += b;
        h2 += c;
        h3 += d;
        h4 += e;
    }
    
    delete[] paddedData;
    
    // Converter para bytes (big-endian)
    hashOut[0] = (h0 >> 24) & 0xFF;
    hashOut[1] = (h0 >> 16) & 0xFF;
    hashOut[2] = (h0 >> 8) & 0xFF;
    hashOut[3] = h0 & 0xFF;
    
    hashOut[4] = (h1 >> 24) & 0xFF;
    hashOut[5] = (h1 >> 16) & 0xFF;
    hashOut[6] = (h1 >> 8) & 0xFF;
    hashOut[7] = h1 & 0xFF;
    
    hashOut[8] = (h2 >> 24) & 0xFF;
    hashOut[9] = (h2 >> 16) & 0xFF;
    hashOut[10] = (h2 >> 8) & 0xFF;
    hashOut[11] = h2 & 0xFF;
    
    hashOut[12] = (h3 >> 24) & 0xFF;
    hashOut[13] = (h3 >> 16) & 0xFF;
    hashOut[14] = (h3 >> 8) & 0xFF;
    hashOut[15] = h3 & 0xFF;
    
    hashOut[16] = (h4 >> 24) & 0xFF;
    hashOut[17] = (h4 >> 16) & 0xFF;
    hashOut[18] = (h4 >> 8) & 0xFF;
    hashOut[19] = h4 & 0xFF;
}

std::string HashUtils::hashToHexString(const uint8_t* hash, size_t size) {
    std::stringstream ss;
    ss << std::hex << std::setfill('0');
    
    for (size_t i = 0; i < size; i++) {
        ss << std::setw(2) << (int)hash[i];
    }
    
    return ss.str();
}
