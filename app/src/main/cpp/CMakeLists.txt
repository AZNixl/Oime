# 轮19.124：手写推理 JNI（薄壳 ✓）
# 链接用 onnxruntime 从 sherpa-onnx AAR 里解出（app/build/ort-link/<abi>/ ✓）
# —— 只用于**链接** ✓，运行时用的是 APK 内 AAR 提供的那份 ✓ ⇒ 不重复打包 ✓
cmake_minimum_required(VERSION 3.22.1)
project(oime_hw CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(ORT_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../build/ort-link/${ANDROID_ABI}")
if(NOT EXISTS "${ORT_DIR}/libonnxruntime.so")
    message(FATAL_ERROR "缺少链接用 libonnxruntime.so：${ORT_DIR}（应从 app/libs/sherpa-onnx-*.aar 解出）")
endif()

add_library(onnxruntime SHARED IMPORTED)
set_target_properties(onnxruntime PROPERTIES
    IMPORTED_LOCATION "${ORT_DIR}/libonnxruntime.so")

add_library(oime_hw SHARED handwriting_jni.cpp)
target_include_directories(oime_hw PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}")
target_link_libraries(oime_hw onnxruntime log)
