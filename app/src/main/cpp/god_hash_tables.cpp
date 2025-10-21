#include "god_hash_tables.h"
#include "hash_utils.h"
#include <android/log.h>
#include <fstream>
#include <cstring>

#define LOG_TAG "GodHashTables"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

GodHashTables::GodHashTables() : blocksInCurrentSub(0) {
    LOGD("GodHashTables initialized");
}

GodHashTables::~GodHashTables() {
    LOGD("GodHashTables destroyed");
}

void GodHashTables::addBlockHash(const uint8_t* hash) {
    // Adicionar hash à sub hash table atual
    for (uint32_t i = 0; i < HASH_SIZE; i++) {
        currentSubTable.push_back(hash[i]);
    }
    
    blocksInCurrentSub++;
    
    // Se completou uma sub hash table (204 blocos)
    if (blocksInCurrentSub >= BLOCKS_PER_SUB) {
        finalizeCurrentSubTable();
    }
}

void GodHashTables::finalizeCurrentSubTable() {
    if (currentSubTable.empty()) {
        return;
    }
    
    // Preencher com zeros se não tiver 204 blocos
    while (blocksInCurrentSub < BLOCKS_PER_SUB) {
        for (uint32_t i = 0; i < HASH_SIZE; i++) {
            currentSubTable.push_back(0);
        }
        blocksInCurrentSub++;
    }
    
    // Adicionar à lista de sub hash tables
    subHashTables.push_back(currentSubTable);
    
    LOGD("Finalized Sub Hash Table #%zu (%zu bytes)",
         subHashTables.size() - 1, currentSubTable.size());
    
    // Resetar para próxima sub table
    currentSubTable.clear();
    blocksInCurrentSub = 0;
}

void GodHashTables::finalize() {
    // Finalizar sub hash table atual se tiver dados
    if (!currentSubTable.empty()) {
        finalizeCurrentSubTable();
    }
    
    // Calcular Master Hash Table
    calculateMasterHash();
    
    LOGD("Finalization complete - Total Sub Hash Tables: %zu", subHashTables.size());
}

void GodHashTables::calculateMasterHash() {
    masterHashTable.clear();
    
    // Master Hash Table contém hashes das Sub Hash Tables
    for (const auto& subTable : subHashTables) {
        uint8_t subHash[20];
        
        // Calcular SHA-1 da Sub Hash Table inteira
        HashUtils::calculateSHA1(subTable.data(), subTable.size(), subHash);
        
        // Adicionar hash à Master Hash Table
        for (uint32_t i = 0; i < HASH_SIZE; i++) {
            masterHashTable.push_back(subHash[i]);
        }
    }
    
    // Preencher Master Hash Table até ter 203 entries
    while (masterHashTable.size() < SUBS_PER_MASTER * HASH_SIZE) {
        for (uint32_t i = 0; i < HASH_SIZE; i++) {
            masterHashTable.push_back(0);
        }
    }
    
    LOGD("Master Hash Table calculated (%zu bytes)", masterHashTable.size());
}

std::vector<uint8_t> GodHashTables::getMasterHashTable() const {
    return masterHashTable;
}

std::vector<uint8_t> GodHashTables::getSubHashTable(uint32_t index) const {
    if (index < subHashTables.size()) {
        return subHashTables[index];
    }
    return std::vector<uint8_t>();
}

uint32_t GodHashTables::getSubHashTableCount() const {
    return subHashTables.size();
}

bool GodHashTables::writeToFile(const std::string& dataFilePath) {
    // TODO: Implementar escrita de hash tables no arquivo Data
    // As hash tables são escritas no início do arquivo Data0000
    
    LOGD("TODO: Write hash tables to: %s", dataFilePath.c_str());
    
    return true;
}
