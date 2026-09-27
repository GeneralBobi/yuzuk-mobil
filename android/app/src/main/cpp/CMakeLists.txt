# Telefonda çevrimdışı yazıya dökme (27.09.2026): bağlantı yokken ses parçası burada metne çevrilir ve bellekten
# atılır. whisper.cpp alt modül olarak durur (cpp/whisper.cpp); yalnız kütüphane derlenir, örnekler ve testler değil.
cmake_minimum_required(VERSION 3.22)
project(yuzuk_whisper C CXX)

set(CMAKE_CXX_STANDARD 17)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
if (ANDROID_ABI STREQUAL "arm64-v8a")
    # Snapdragon 8 ve sonrası: fp16 ve nokta çarpım komutları yazıya dökmeyi birkaç kat hızlandırır.
    set(GGML_CPU_ARM_ARCH "armv8.2-a+fp16+dotprod" CACHE STRING "" FORCE)
elseif (ANDROID_ABI STREQUAL "x86_64")
    # Yalnız emülatör (bilgisayarın işlemcisini görür): vektör komutları kapalıyken bir parça dakikalar sürüyor.
    set(GGML_AVX ON CACHE BOOL "" FORCE)
    set(GGML_AVX2 ON CACHE BOOL "" FORCE)
    set(GGML_FMA ON CACHE BOOL "" FORCE)
    set(GGML_F16C ON CACHE BOOL "" FORCE)
endif ()

add_subdirectory(whisper.cpp)

add_library(yuzukwhisper SHARED yuzuk_whisper.cpp)
target_link_libraries(yuzukwhisper PRIVATE whisper log android)
if (NOT CMAKE_BUILD_TYPE STREQUAL "Debug")
    target_compile_options(yuzukwhisper PRIVATE -O3)
endif ()
