#ifndef XEX_PARSER_H
#define XEX_PARSER_H

#include <cstdint>
#include <string>

struct XexExecutionInfo {
    uint8_t mediaId[4];
    uint32_t version;
    uint32_t baseVersion;
    uint8_t titleId[4];
    uint8_t platform;
    uint8_t executableType;
    uint8_t discNumber;
    uint8_t discCount;
};

class XexParser {
public:
    XexParser();
    ~XexParser();
    
    bool parse(const uint8_t* xexData, size_t size);
    
    std::string getTitleIdString() const;
    std::string getMediaIdString() const;
    XexExecutionInfo getExecutionInfo() const { return execInfo; }
    
private:
    XexExecutionInfo execInfo;
    bool valid;
    
    uint32_t readUInt32BE(const uint8_t* data);
};

#endif // XEX_PARSER_H
