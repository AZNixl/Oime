/**
 * 轮19.124：手写识别 ONNX 推理 JNI（**薄壳** ✓）
 * · 复用包内已有的 libonnxruntime.so（来自 sherpa-onnx AAR ✓）⇒ 体积增量≈0 ✓
 * · 只提供三件事：init(模型路径) / run(输入 float 张量 → 输出分数) / release
 * · 模型：[1,3,120,120] float（0..255 ✓）→ [1,7354] softmax ✓
 *   （top-k 由 Kotlin 侧做 ✓，避免在 C++ 里处理标签表）
 */
#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>

#include "onnxruntime_c_api.h"

#define LOG_TAG "OimeHwJni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
const OrtApi* g_api = nullptr;
OrtEnv* g_env = nullptr;
OrtSessionOptions* g_opts = nullptr;
OrtSession* g_sess = nullptr;
OrtAllocator* g_alloc = nullptr;
std::string g_inName, g_outName, g_lastErr;
std::vector<int64_t> g_inShape, g_outShape;
constexpr int kInElements = 1 * 3 * 120 * 120;

void setErr(const char* msg) { g_lastErr = msg ? msg : ""; }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_azime_input_core_handwriting_HandwritingNative_nativeLastError(JNIEnv* env, jclass) {
    return env->NewStringUTF(g_lastErr.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_azime_input_core_handwriting_HandwritingNative_nativeInit(
        JNIEnv* env, jclass, jstring modelPath, jint threads) {
    if (g_sess != nullptr) return JNI_TRUE;
    g_lastErr.clear();
    if (g_api == nullptr) {
        g_api = OrtGetApiBase()->GetApi(ORT_API_VERSION);
        if (g_api == nullptr) { setErr("OrtGetApiBase 失败"); return JNI_FALSE; }
        if (g_api->CreateEnv(ORT_LOGGING_LEVEL_WARNING, "oime-hw", &g_env) != nullptr) {
            setErr("CreateEnv 失败"); return JNI_FALSE;
        }
        g_api->CreateSessionOptions(&g_opts);
        g_api->SetIntraOpNumThreads(g_opts, threads > 0 ? threads : 2);
        g_api->SetSessionGraphOptimizationLevel(g_opts, ORT_ENABLE_ALL);
        g_api->GetAllocatorWithDefaultOptions(&g_alloc);
    }
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    OrtStatus* st = g_api->CreateSession(g_env, path, g_opts, &g_sess);
    env->ReleaseStringUTFChars(modelPath, path);
    if (st != nullptr) {
        setErr(g_api->GetErrorMessage(st));
        g_api->ReleaseStatus(st);
        g_sess = nullptr;
        return JNI_FALSE;
    }
    // 取输入/输出名与形状 ✓
    OrtAllocator* alloc = nullptr;
    g_api->GetAllocatorWithDefaultOptions(&alloc);
    char* inName = nullptr;
    g_api->SessionGetInputName(g_sess, 0, alloc, &inName);
    g_inName = inName ? inName : "";
    char* outName = nullptr;
    g_api->SessionGetOutputName(g_sess, 0, alloc, &outName);
    g_outName = outName ? outName : "";

    OrtTypeInfo* ti = nullptr;
    if (g_api->SessionGetInputTypeInfo(g_sess, 0, &ti) == nullptr) {
        const OrtTensorTypeAndShapeInfo* tsi = nullptr;
        g_api->CastTypeInfoToTensorInfo(ti, &tsi);
        size_t n = 0;
        g_api->GetDimensionsCount(tsi, &n);
        g_inShape.assign(n, 0);
        g_api->GetDimensions(tsi, g_inShape.data(), n);
        g_api->ReleaseTypeInfo(ti);
    }
    ti = nullptr;
    if (g_api->SessionGetOutputTypeInfo(g_sess, 0, &ti) == nullptr) {
        const OrtTensorTypeAndShapeInfo* tsi = nullptr;
        g_api->CastTypeInfoToTensorInfo(ti, &tsi);
        size_t n = 0;
        g_api->GetDimensionsCount(tsi, &n);
        g_outShape.assign(n, 0);
        g_api->GetDimensions(tsi, g_outShape.data(), n);
        g_api->ReleaseTypeInfo(ti);
    }
    LOGI("模型已加载: in=%s out=%s", g_inName.c_str(), g_outName.c_str());
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_azime_input_core_handwriting_HandwritingNative_nativeReady(JNIEnv*, jclass) {
    return g_sess != nullptr ? JNI_TRUE : JNI_FALSE;
}

/** 输入：float[kInElements]（1×3×120×120 ✓）；输出：float[numClasses]（softmax 分数 ✓）*/
extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_azime_input_core_handwriting_HandwritingNative_nativeRun(
        JNIEnv* env, jclass, jfloatArray input) {
    if (g_sess == nullptr) { setErr("模型未加载"); return nullptr; }
    jsize n = env->GetArrayLength(input);
    if (n < kInElements) { setErr("输入长度不足"); return nullptr; }
    jfloat* in = env->GetFloatArrayElements(input, nullptr);

    std::vector<int64_t> shape = g_inShape.empty()
        ? std::vector<int64_t>{1, 3, 120, 120} : g_inShape;
    int64_t outDim = (g_outShape.size() >= 2) ? g_outShape[g_outShape.size() - 1] : 7354;

    OrtMemoryInfo* memInfo = nullptr;
    g_api->CreateCpuMemoryInfo(OrtArenaAllocator, OrtMemTypeDefault, &memInfo);
    OrtValue* inTensor = nullptr;
    OrtStatus* st = g_api->CreateTensorWithDataAsOrtValue(
        memInfo, in, (size_t) kInElements * sizeof(float),
        shape.data(), shape.size(), ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT, &inTensor);
    if (st != nullptr) { setErr(g_api->GetErrorMessage(st)); g_api->ReleaseStatus(st); env->ReleaseFloatArrayElements(input, in, JNI_ABORT); return nullptr; }

    const char* inNames[1] = { g_inName.c_str() };
    const char* outNames[1] = { g_outName.c_str() };
    OrtValue* outTensor = nullptr;
    st = g_api->Run(g_sess, nullptr, inNames, (const OrtValue* const*) &inTensor, 1,
                    outNames, 1, &outTensor);
    g_api->ReleaseValue(inTensor);
    env->ReleaseFloatArrayElements(input, in, JNI_ABORT);
    if (st != nullptr) { setErr(g_api->GetErrorMessage(st)); g_api->ReleaseStatus(st); return nullptr; }

    float* outPtr = nullptr;
    g_api->GetTensorMutableData(outTensor, (void**) &outPtr);
    jfloatArray result = env->NewFloatArray((jsize) outDim);
    if (result != nullptr && outPtr != nullptr) {
        env->SetFloatArrayRegion(result, 0, (jsize) outDim, outPtr);
    }
    g_api->ReleaseValue(outTensor);
    g_api->ReleaseMemoryInfo(memInfo);
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_azime_input_core_handwriting_HandwritingNative_nativeRelease(JNIEnv*, jclass) {
    if (g_sess) { g_api->ReleaseSession(g_sess); g_sess = nullptr; }
    if (g_opts) { g_api->ReleaseSessionOptions(g_opts); g_opts = nullptr; }
    if (g_env) { g_api->ReleaseEnv(g_env); g_env = nullptr; }
    g_api = nullptr;
}
