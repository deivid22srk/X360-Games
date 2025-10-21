#ifndef GDF_PARSER_H
#define GDF_PARSER_H

#include <cstdint>
#include <string>
#include <vector>

// Game Disc Format (GDF) parser para Xbox 360 ISO
// TODO: Implementar parsing completo do GDF

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
    
private:
    std::vector<GDFEntry> entries;
};

#endif // GDF_PARSER_H
