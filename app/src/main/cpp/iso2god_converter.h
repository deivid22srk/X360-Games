#ifndef ISO2GOD_CONVERTER_H
#define ISO2GOD_CONVERTER_H

#include <string>
#include <cstdint>
#include <functional>

struct IsoInfo {
    std::string gameName;
    std::string titleId;
    std::string mediaId;
    std::string platform;
    uint64_t sizeBytes;
    std::string volumeDescriptor;
};

using ProgressCallback = std::function<void(float progress, const std::string& status)>;

class Iso2GodConverter {
public:
    Iso2GodConverter();
    ~Iso2GodConverter();
    
    int convertIsoToGod(
        const std::string& isoPath,
        const std::string& outputPath,
        ProgressCallback progressCallback
    );
    
    IsoInfo* getIsoInfo(const std::string& isoPath);
    
    void cancelConversion();
    
private:
    bool cancelled;
    
    static const uint32_t BLOCK_SIZE = 4096;
    static const uint32_t SHT_PER_MHT = 203;
    static const uint32_t BLOCK_PER_SHT = 204;
    static const uint32_t BLOCK_PER_PART = 41412;
    
    bool readIsoHeader(const std::string& isoPath, IsoInfo& info);
    bool createGodStructure(const std::string& outputPath, const IsoInfo& info);
    bool convertData(
        const std::string& isoPath,
        const std::string& outputPath,
        const IsoInfo& info,
        ProgressCallback progressCallback
    );
    bool writeHashTables(const std::string& outputPath, const IsoInfo& info);
};

#endif // ISO2GOD_CONVERTER_H
