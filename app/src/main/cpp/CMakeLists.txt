cmake_minimum_required(VERSION 3.22.1)
project(verbaocr CXX)

# ncnn's prebuilt Android release; the path comes from Gradle (NCNN_ROOT)
set(ncnn_DIR "${NCNN_ROOT}/${ANDROID_ABI}/lib/cmake/ncnn")
find_package(ncnn REQUIRED)

add_library(verbaocr SHARED verba_ocr.cpp)
target_compile_features(verbaocr PRIVATE cxx_std_17)
target_compile_options(verbaocr PRIVATE -O2 -fvisibility=hidden -ffunction-sections -fdata-sections)
target_link_libraries(verbaocr PRIVATE ncnn jnigraphics log)
target_link_options(verbaocr PRIVATE
    # OpenMP into the library itself, no extra libomp.so
    -fopenmp -static-openmp
    -Wl,--gc-sections
    -Wl,--exclude-libs,ALL
    # 16 KB page alignment (Android 15+ devices with 16 KB pages)
    -Wl,-z,max-page-size=16384)
