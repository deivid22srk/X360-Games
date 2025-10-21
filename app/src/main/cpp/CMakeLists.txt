cmake_minimum_required(VERSION 3.22.1)

project("iso2god")

# Configurar C++17
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Adicionar flags de compilação
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wall -Wextra -O3")

# Incluir diretórios
include_directories(${CMAKE_CURRENT_SOURCE_DIR})

# Arquivos fonte
set(SOURCE_FILES
    iso2god_jni.cpp
    iso2god_converter.cpp
    gdf_parser.cpp
    xex_parser.cpp
    hash_utils.cpp
    god_hash_tables.cpp
)

# Criar biblioteca compartilhada
add_library(${CMAKE_PROJECT_NAME} SHARED ${SOURCE_FILES})

# Linkar com bibliotecas do Android
find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(${CMAKE_PROJECT_NAME}
    ${log-lib}
    ${android-lib}
)
