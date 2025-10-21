#ifndef HASH_UTILS_H
#define HASH_UTILS_H

#include <cstdint>
#include <string>

// Utilitários para SHA-1 hashing (necessário para GOD format)
// TODO: Implementar SHA-1 completo ou usar biblioteca existente

class HashUtils {
public:
    // Calcula SHA-1 de dados
    static void calculateSHA1(const uint8_t* data, size_t size, uint8_t* hashOut);
    
    // Converte hash para string hex
    static std::string hashToHexString(const uint8_t* hash, size_t size);
    
private:
    // TODO: Implementar SHA-1 algorithm
};

#endif // HASH_UTILS_H
