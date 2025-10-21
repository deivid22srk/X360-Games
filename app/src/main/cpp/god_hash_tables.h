#ifndef GOD_HASH_TABLES_H
#define GOD_HASH_TABLES_H

#include <cstdint>
#include <vector>
#include <string>

class GodHashTables {
public:
    GodHashTables();
    ~GodHashTables();
    
    void addBlockHash(const uint8_t* hash);
    
    void finalize();
    
    std::vector<uint8_t> getMasterHashTable() const;
    
    std::vector<uint8_t> getSubHashTable(uint32_t index) const;
    
    uint32_t getSubHashTableCount() const;
    
    bool writeToFile(const std::string& dataFilePath);
    
private:
    std::vector<std::vector<uint8_t>> subHashTables;
    std::vector<uint8_t> masterHashTable;
    std::vector<uint8_t> currentSubTable;
    uint32_t blocksInCurrentSub;
    
    static const uint32_t HASH_SIZE = 20;
    static const uint32_t BLOCKS_PER_SUB = 204;
    static const uint32_t SUBS_PER_MASTER = 203;
    
    void finalizeCurrentSubTable();
    void calculateMasterHash();
};

#endif // GOD_HASH_TABLES_H
