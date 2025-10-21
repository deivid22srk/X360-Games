#include "iso2god_converter.h"
#include "gdf_parser.h"
#include "xex_parser.h"
#include "hash_utils.h"
#include "god_hash_tables.h"
#include <fstream>
#include <cstring>
#include <sys/stat.h>
#include <android/log.h>

#define LOG_TAG "Iso2God-Native"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

Iso2GodConverter::Iso2GodConverter() : cancelled(false) {
    LOGD("Iso2GodConverter initialized");
}

Iso2GodConverter::~Iso2GodConverter() {
    LOGD("Iso2GodConverter destroyed");
}

int Iso2GodConverter::convertIsoToGod(
    const std::string& isoPath,
    const std::string& outputPath,
    ProgressCallback progressCallback
) {
    cancelled = false;
    
    LOGD("=== Starting ISO to GOD Conversion ===");
    LOGD("ISO: %s", isoPath.c_str());
    LOGD("Output: %s", outputPath.c_str());
    
    try {
        progressCallback(0.05f, "Analisando ISO...");
        
        IsoInfo info;
        if (!readIsoHeader(isoPath, info)) {
            LOGE("Failed to read ISO header");
            return -1;
        }
        
        if (cancelled) return -4;
        
        LOGD("ISO Information:");
        LOGD("  Game: %s", info.gameName.c_str());
        LOGD("  Title ID: %s", info.titleId.c_str());
        LOGD("  Media ID: %s", info.mediaId.c_str());
        LOGD("  Size: %llu MB", info.sizeBytes / 1024 / 1024);
        
        progressCallback(0.1f, "Criando estrutura GOD...");
        
        if (!createGodStructure(outputPath, info)) {
            LOGE("Failed to create GOD structure");
            return -2;
        }
        
        if (cancelled) return -4;
        
        progressCallback(0.15f, "Convertendo dados...");
        
        if (!convertData(isoPath, outputPath, info, progressCallback)) {
            LOGE("Failed to convert data");
            return -3;
        }
        
        progressCallback(1.0f, "Conversão concluída!");
        LOGD("=== Conversion completed successfully ===");
        return 0;
        
    } catch (const std::exception& e) {
        LOGE("Exception during conversion: %s", e.what());
        return -3;
    }
}

IsoInfo* Iso2GodConverter::getIsoInfo(const std::string& isoPath) {
    LOGD("Getting ISO info: %s", isoPath.c_str());
    
    IsoInfo* info = new IsoInfo();
    
    if (!readIsoHeader(isoPath, *info)) {
        LOGE("Failed to read ISO header");
        delete info;
        return nullptr;
    }
    
    return info;
}

void Iso2GodConverter::cancelConversion() {
    LOGD("Cancellation requested");
    cancelled = true;
}

bool Iso2GodConverter::readIsoHeader(const std::string& isoPath, IsoInfo& info) {
    LOGD("Reading ISO header: %s", isoPath.c_str());
    
    GDFParser gdfParser;
    if (!gdfParser.parse(isoPath)) {
        LOGE("Failed to parse GDF");
        return false;
    }
    
    GDFEntry* xexEntry = gdfParser.findFile("default.xex");
    if (!xexEntry) {
        LOGE("default.xex not found in ISO");
        return false;
    }
    
    LOGD("Found default.xex at sector %u, size %u", xexEntry->sector, xexEntry->size);
    
    std::ifstream iso(isoPath, std::ios::binary);
    if (!iso.is_open()) {
        delete xexEntry;
        return false;
    }
    
    const uint32_t ROOT_OFFSET = 0xFDA000;
    const uint32_t SECTOR_SIZE = 2048;
    
    uint64_t xexOffset = ROOT_OFFSET + (uint64_t)xexEntry->sector * SECTOR_SIZE;
    iso.seekg(xexOffset);
    
    uint8_t* xexData = new uint8_t[xexEntry->size];
    iso.read((char*)xexData, xexEntry->size);
    iso.close();
    
    XexParser xexParser;
    if (!xexParser.parse(xexData, xexEntry->size)) {
        LOGE("Failed to parse XEX");
        delete[] xexData;
        delete xexEntry;
        return false;
    }
    
    info.titleId = xexParser.getTitleIdString();
    info.mediaId = xexParser.getMediaIdString();
    info.gameName = xexEntry->name;
    info.platform = "Xbox 360";
    
    std::ifstream isoFile(isoPath, std::ios::binary);
    isoFile.seekg(0, std::ios::end);
    info.sizeBytes = isoFile.tellg();
    isoFile.close();
    
    info.volumeDescriptor = "XBOX360";
    
    delete[] xexData;
    delete xexEntry;
    
    LOGD("ISO Header read successfully");
    return true;
}

bool Iso2GodConverter::createGodStructure(
    const std::string& outputPath,
    const IsoInfo& info
) {
    LOGD("Creating GOD structure at: %s", outputPath.c_str());
    
    std::string titlePath = outputPath + "/" + info.titleId;
    std::string contentPath = titlePath + "/Content";
    std::string dataPath = contentPath + "/0000000000000000";
    
    auto createDir = [](const std::string& path) -> bool {
        struct stat st = {0};
        if (stat(path.c_str(), &st) == -1) {
            if (mkdir(path.c_str(), 0755) != 0) {
                return false;
            }
        }
        return true;
    };
    
    if (!createDir(outputPath) || !createDir(titlePath) || 
        !createDir(contentPath) || !createDir(dataPath)) {
        LOGE("Failed to create GOD directory structure");
        return false;
    }
    
    LOGD("GOD structure created successfully");
    return true;
}

bool Iso2GodConverter::convertData(
    const std::string& isoPath,
    const std::string& outputPath,
    const IsoInfo& info,
    ProgressCallback progressCallback
) {
    LOGD("Starting data conversion with hash tables");
    
    std::ifstream isoFile(isoPath, std::ios::binary);
    if (!isoFile.is_open()) {
        LOGE("Failed to open ISO for reading");
        return false;
    }
    
    std::string dataBasePath = outputPath + "/" + info.titleId + "/Content/0000000000000000/Data";
    
    uint64_t totalBytes = info.sizeBytes;
    uint64_t processedBytes = 0;
    uint32_t currentPart = 0;
    uint32_t bytesInCurrentPart = 0;
    uint32_t totalBlocks = 0;
    
    const uint32_t MAX_PART_SIZE = BLOCK_PER_PART * BLOCK_SIZE;
    
    GodHashTables hashTables;
    
    uint8_t* block = new uint8_t[BLOCK_SIZE];
    
    char partName[256];
    snprintf(partName, sizeof(partName), "%s%04d", dataBasePath.c_str(), currentPart);
    
    std::ofstream dataFile(partName, std::ios::binary);
    if (!dataFile.is_open()) {
        LOGE("Failed to create Data file: %s", partName);
        delete[] block;
        isoFile.close();
        return false;
    }
    
    LOGD("Processing ISO blocks...");
    
    while (processedBytes < totalBytes && !cancelled) {
        memset(block, 0, BLOCK_SIZE);
        
        size_t toRead = std::min((uint64_t)BLOCK_SIZE, totalBytes - processedBytes);
        isoFile.read((char*)block, toRead);
        size_t actualRead = isoFile.gcount();
        
        if (actualRead == 0 && processedBytes < totalBytes) {
            LOGE("Failed to read from ISO at offset %llu", processedBytes);
            break;
        }
        
        uint8_t hash[20];
        HashUtils::calculateSHA1(block, BLOCK_SIZE, hash);
        hashTables.addBlockHash(hash);
        
        dataFile.write((char*)block, BLOCK_SIZE);
        
        processedBytes += actualRead;
        bytesInCurrentPart += BLOCK_SIZE;
        totalBlocks++;
        
        if (bytesInCurrentPart >= MAX_PART_SIZE && processedBytes < totalBytes) {
            dataFile.close();
            currentPart++;
            bytesInCurrentPart = 0;
            
            snprintf(partName, sizeof(partName), "%s%04d", dataBasePath.c_str(), currentPart);
            dataFile.open(partName, std::ios::binary);
            
            if (!dataFile.is_open()) {
                LOGE("Failed to create Data file: %s", partName);
                delete[] block;
                isoFile.close();
                return false;
            }
            
            LOGD("Created Data file part %u: %s", currentPart, partName);
        }
        
        if (totalBlocks % 1000 == 0 || processedBytes >= totalBytes) {
            float progress = 0.15f + (0.75f * ((float)processedBytes / (float)totalBytes));
            char status[128];
            snprintf(status, sizeof(status), "Bloco %u de %llu (%.1f%%)",
                     totalBlocks, totalBytes / BLOCK_SIZE,
                     (float)processedBytes * 100.0f / (float)totalBytes);
            progressCallback(progress, status);
        }
    }
    
    delete[] block;
    dataFile.close();
    isoFile.close();
    
    if (cancelled) {
        LOGD("Conversion cancelled by user");
        return false;
    }
    
    LOGD("Data conversion completed");
    LOGD("  Total blocks: %u", totalBlocks);
    LOGD("  Data files created: %u", currentPart + 1);
    
    progressCallback(0.9f, "Finalizando hash tables...");
    
    hashTables.finalize();
    
    progressCallback(0.95f, "Escrevendo hash tables...");
    
    if (!writeHashTables(outputPath, info)) {
        LOGE("Failed to write hash tables");
        return false;
    }
    
    return true;
}

bool Iso2GodConverter::writeHashTables(
    const std::string& outputPath,
    const IsoInfo& info
) {
    LOGD("Writing hash tables");
    
    std::string data0000Path = outputPath + "/" + info.titleId + "/Content/0000000000000000/Data0000";
    
    LOGD("TODO: Write hash tables to beginning of Data0000");
    LOGD("  File: %s", data0000Path.c_str());
    
    return true;
}
