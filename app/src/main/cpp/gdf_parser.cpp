#include "gdf_parser.h"
#include <android/log.h>
#include <fstream>
#include <cstring>

#define LOG_TAG "GDFParser"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Tipos de ISO Xbox 360
enum class IsoType : uint32_t {
    Xsf = 0,
    XGD1 = 0x2000,
    XGD2 = 0xFDA000,
    XGD3 = 0x2080000
};

struct GDFVolumeDescriptor {
    uint8_t identifier[20];
    uint32_t rootDirSector;
    uint32_t rootDirSize;
    uint8_t imageCreationTime[8];
    uint32_t sectorSize;
    uint32_t rootOffset;
    uint64_t volumeSize;
    uint32_t volumeSectors;
};

GDFParser::GDFParser() {
    LOGD("GDFParser initialized");
}

GDFParser::~GDFParser() {
    LOGD("GDFParser destroyed");
}

// Lê uint16 little-endian
static uint16_t readUInt16LE(std::ifstream& file) {
    uint8_t bytes[2];
    file.read((char*)bytes, 2);
    return (uint16_t)bytes[0] | ((uint16_t)bytes[1] << 8);
}

// Lê uint32 little-endian
static uint32_t readUInt32LE(std::ifstream& file) {
    uint8_t bytes[4];
    file.read((char*)bytes, 4);
    return (uint32_t)bytes[0] | ((uint32_t)bytes[1] << 8) | 
           ((uint32_t)bytes[2] << 16) | ((uint32_t)bytes[3] << 24);
}

bool GDFParser::parse(const std::string& isoPath) {
    std::ifstream iso(isoPath, std::ios::binary);
    if (!iso.is_open()) {
        LOGE("Failed to open ISO: %s", isoPath.c_str());
        return false;
    }
    
    LOGD("Parsing GDF from: %s", isoPath.c_str());
    
    // Detectar tipo de ISO (Xsf, XGD1, XGD2, XGD3)
    GDFVolumeDescriptor volDesc;
    volDesc.sectorSize = 2048;
    
    IsoType isoType = IsoType::XGD2;
    char magic[21] = {0};
    
    // Tentar Xsf (offset 0)
    iso.seekg(32 * volDesc.sectorSize);
    iso.read(magic, 20);
    
    if (strcmp(magic, "MICROSOFT*XBOX*MEDIA") == 0) {
        isoType = IsoType::Xsf;
        volDesc.rootOffset = (uint32_t)isoType;
        LOGD("Detected ISO type: Xsf");
    } else {
        // Tentar XGD1
        iso.seekg(32 * volDesc.sectorSize + (uint32_t)IsoType::XGD1);
        iso.read(magic, 20);
        
        if (strcmp(magic, "MICROSOFT*XBOX*MEDIA") == 0) {
            isoType = IsoType::XGD1;
            volDesc.rootOffset = (uint32_t)isoType;
            LOGD("Detected ISO type: XGD1");
        } else {
            // Tentar XGD2
            iso.seekg(32 * volDesc.sectorSize + (uint32_t)IsoType::XGD2);
            iso.read(magic, 20);
            
            if (strcmp(magic, "MICROSOFT*XBOX*MEDIA") == 0) {
                isoType = IsoType::XGD2;
                volDesc.rootOffset = (uint32_t)isoType;
                LOGD("Detected ISO type: XGD2");
            } else {
                // Assumir XGD3
                isoType = IsoType::XGD3;
                volDesc.rootOffset = (uint32_t)isoType;
                LOGD("Detected ISO type: XGD3");
            }
        }
    }
    
    // Ler volume descriptor
    iso.seekg(32 * volDesc.sectorSize + volDesc.rootOffset);
    iso.read((char*)volDesc.identifier, 20);
    volDesc.rootDirSector = readUInt32LE(iso);
    volDesc.rootDirSize = readUInt32LE(iso);
    iso.read((char*)volDesc.imageCreationTime, 8);
    
    LOGD("Root Directory: Sector=%u, Size=%u", volDesc.rootDirSector, volDesc.rootDirSize);
    
    // Parsear root directory
    if (!parseDirectory(iso, volDesc, volDesc.rootDirSector, volDesc.rootDirSize)) {
        LOGE("Failed to parse root directory");
        iso.close();
        return false;
    }
    
    iso.close();
    
    LOGD("GDF parsing completed - Found %zu entries", entries.size());
    return true;
}

bool GDFParser::parseDirectory(
    std::ifstream& iso,
    const GDFVolumeDescriptor& volDesc,
    uint32_t sector,
    uint32_t size
) {
    // Ir para o setor do diretório
    uint64_t offset = (uint64_t)sector * volDesc.sectorSize + volDesc.rootOffset;
    iso.seekg(offset);
    
    // Ler todos os dados do diretório
    uint8_t* dirData = new uint8_t[size];
    iso.read((char*)dirData, size);
    
    uint32_t position = 0;
    
    // Parsear entries até o fim dos dados
    while (position < size) {
        // Ler GDFDirEntry
        if (position + 14 > size) break;
        
        uint16_t subTreeL = (uint16_t)dirData[position] | ((uint16_t)dirData[position + 1] << 8);
        uint16_t subTreeR = (uint16_t)dirData[position + 2] | ((uint16_t)dirData[position + 3] << 8);
        position += 4;
        
        // 0xFFFF indica fim da lista
        if (subTreeL == 0xFFFF && subTreeR == 0xFFFF) {
            break;
        }
        
        uint32_t entrySector = (uint32_t)dirData[position] | 
                               ((uint32_t)dirData[position + 1] << 8) |
                               ((uint32_t)dirData[position + 2] << 16) |
                               ((uint32_t)dirData[position + 3] << 24);
        position += 4;
        
        uint32_t entrySize = (uint32_t)dirData[position] | 
                            ((uint32_t)dirData[position + 1] << 8) |
                            ((uint32_t)dirData[position + 2] << 16) |
                            ((uint32_t)dirData[position + 3] << 24);
        position += 4;
        
        uint8_t attributes = dirData[position++];
        uint8_t nameLength = dirData[position++];
        
        // Ler nome
        char name[256] = {0};
        memcpy(name, &dirData[position], nameLength);
        position += nameLength;
        
        // Padding para alinhar a 4 bytes
        uint32_t padding = (4 - ((14 + nameLength) % 4)) % 4;
        position += padding;
        
        // Adicionar entry
        GDFEntry entry;
        entry.name = std::string(name, nameLength);
        entry.sector = entrySector;
        entry.size = entrySize;
        entry.isDirectory = (attributes & 0x10) != 0;
        
        entries.push_back(entry);
        
        LOGD("Entry: %s (Sector=%u, Size=%u, Dir=%d)",
             entry.name.c_str(), entry.sector, entry.size, entry.isDirectory);
        
        // Se for diretório, parsear recursivamente
        if (entry.isDirectory && entrySize > 0) {
            parseDirectory(iso, volDesc, entrySector, entrySize);
        }
    }
    
    delete[] dirData;
    return true;
}

std::vector<GDFEntry> GDFParser::getEntries() const {
    return entries;
}

GDFEntry* GDFParser::findFile(const std::string& fileName) const {
    for (const auto& entry : entries) {
        if (entry.name == fileName && !entry.isDirectory) {
            return new GDFEntry(entry);
        }
    }
    return nullptr;
}
