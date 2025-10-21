#ifndef GDF_PARSER_H
#define GDF_PARSER_H

#include <cstdint>
#include <string>
#include <vector>
#include <fstream>

struct GDFVolumeDescriptor;

struct GDFEntry {
    std::string name;
    uint32_t sector;
    uint32_t size;
    bool isDirectory;
};

class GDFParser {
public:
    GDFParser();
    ~GDFParser();
    
    bool parse(const std::string& isoPath);
    std::vector<GDFEntry> getEntries() const;
    GDFEntry* findFile(const std::string& fileName) const;
    
private:
    std::vector<GDFEntry> entries;
    
    bool parseDirectory(
        std::ifstream& iso,
        const GDFVolumeDescriptor& volDesc,
        uint32_t sector,
        uint32_t size
    );
};

#endif // GDF_PARSER_H
