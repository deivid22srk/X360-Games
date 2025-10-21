#ifndef ISO2GOD_CONVERTER_H
#define ISO2GOD_CONVERTER_H

#include <string>
#include <cstdint>
#include <functional>

// Estrutura de informações do ISO
struct IsoInfo {
    std::string gameName;
    std::string titleId;
    std::string mediaId;
    std::string platform;
    uint64_t sizeBytes;
    std::string volumeDescriptor;
};

// Callback de progresso
using ProgressCallback = std::function<void(float progress, const std::string& status)>;

// Classe principal do conversor
class Iso2GodConverter {
public:
    Iso2GodConverter();
    ~Iso2GodConverter();
    
    // Converte ISO para GOD
    // Retorna: 0 = sucesso, <0 = erro
    int convertIsoToGod(
        const std::string& isoPath,
        const std::string& outputPath,
        ProgressCallback progressCallback
    );
    
    // Obtém informações do ISO
    IsoInfo* getIsoInfo(const std::string& isoPath);
    
    // Cancela conversão em andamento
    void cancelConversion();
    
private:
    bool cancelled;
    
    // Constantes
    static const uint32_t BLOCK_SIZE = 4096;
    static const uint32_t SHT_PER_MHT = 203;
    static const uint32_t BLOCK_PER_SHT = 204;
    static const uint32_t BLOCK_PER_PART = 41412;
    
    // Funções auxiliares
    bool readIsoHeader(const std::string& isoPath, IsoInfo& info);
    bool createGodStructure(const std::string& outputPath, const IsoInfo& info);
    bool convertData(
        const std::string& isoPath,
        const std::string& outputPath,
        const IsoInfo& info,
        ProgressCallback progressCallback
    );
    void calculateHashes(uint8_t* data, size_t size, uint8_t* hashOut);
};

#endif // ISO2GOD_CONVERTER_H
