// Bridge between Verba and ncnn: runs PaddleOCR's text detection and
// recognition networks. Everything around them (finding boxes in the
// probability map, decoding characters, reading order) is Kotlin, see
// io.github.lenkr0.verba.ocr.

#include <android/bitmap.h>
#include <jni.h>

#include <algorithm>
#include <cmath>
#include <memory>
#include <vector>

#include <net.h>

namespace {

// Both networks expect BGR scaled to [-1, 1]
constexpr float kMean[3] = {127.5f, 127.5f, 127.5f};
constexpr float kNorm[3] = {1 / 127.5f, 1 / 127.5f, 1 / 127.5f};
constexpr int kLineHeight = 48;
// Short lines are padded to this width, as PaddleOCR does
constexpr int kMinLineWidth = 160;

struct Engine {
    ncnn::Net det;
    ncnn::Net rec;
};

Engine* engine(jlong handle) { return reinterpret_cast<Engine*>(handle); }

void configure(ncnn::Net& net, int threads) {
    net.opt.num_threads = threads;
    net.opt.use_vulkan_compute = false;
    // fp16 storage halves memory traffic; arithmetic stays fp32 for accuracy
    net.opt.use_fp16_storage = true;
    net.opt.use_fp16_packed = true;
    net.opt.use_fp16_arithmetic = false;
}

bool load(ncnn::Net& net, JNIEnv* env, jstring param, jstring model) {
    const char* p = env->GetStringUTFChars(param, nullptr);
    const char* m = env->GetStringUTFChars(model, nullptr);
    bool ok = net.load_param(p) == 0 && net.load_model(m) == 0;
    env->ReleaseStringUTFChars(param, p);
    env->ReleaseStringUTFChars(model, m);
    return ok;
}

struct LockedBitmap {
    JNIEnv* env;
    jobject bitmap;
    AndroidBitmapInfo info{};
    const unsigned char* pixels = nullptr;

    LockedBitmap(JNIEnv* e, jobject b) : env(e), bitmap(b) {
        void* data = nullptr;
        if (AndroidBitmap_getInfo(env, bitmap, &info) == ANDROID_BITMAP_RESULT_SUCCESS &&
            info.format == ANDROID_BITMAP_FORMAT_RGBA_8888 &&
            AndroidBitmap_lockPixels(env, bitmap, &data) == ANDROID_BITMAP_RESULT_SUCCESS) {
            pixels = static_cast<const unsigned char*>(data);
        }
    }

    ~LockedBitmap() {
        if (pixels) AndroidBitmap_unlockPixels(env, bitmap);
    }
};

// Bilinear sampling of an affine-mapped region; coordinates outside the
// bitmap repeat its edge (like OpenCV's BORDER_REPLICATE in PaddleOCR).
// m maps target pixel centers to source coordinates: sx = m0*x + m1*y + m2, sy = m3*x + m4*y + m5.
void warp(const LockedBitmap& src, const float* m, int w, int h, unsigned char* out) {
    const int sw = static_cast<int>(src.info.width);
    const int sh = static_cast<int>(src.info.height);
    const int stride = static_cast<int>(src.info.stride);
    for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
            float fx = m[0] * (x + 0.5f) + m[1] * (y + 0.5f) + m[2] - 0.5f;
            float fy = m[3] * (x + 0.5f) + m[4] * (y + 0.5f) + m[5] - 0.5f;
            fx = std::clamp(fx, 0.f, static_cast<float>(sw - 1));
            fy = std::clamp(fy, 0.f, static_cast<float>(sh - 1));
            int x0 = static_cast<int>(fx), y0 = static_cast<int>(fy);
            int x1 = std::min(x0 + 1, sw - 1), y1 = std::min(y0 + 1, sh - 1);
            float ax = fx - x0, ay = fy - y0;
            const unsigned char* p00 = src.pixels + y0 * stride + x0 * 4;
            const unsigned char* p01 = src.pixels + y0 * stride + x1 * 4;
            const unsigned char* p10 = src.pixels + y1 * stride + x0 * 4;
            const unsigned char* p11 = src.pixels + y1 * stride + x1 * 4;
            unsigned char* o = out + (y * w + x) * 4;
            for (int c = 0; c < 4; c++) {
                float top = p00[c] + (p01[c] - p00[c]) * ax;
                float bottom = p10[c] + (p11[c] - p10[c]) * ax;
                o[c] = static_cast<unsigned char>(std::lround(top + (bottom - top) * ay));
            }
        }
    }
}

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_io_github_lenkr0_verba_ocr_OcrNative_load(JNIEnv* env, jclass, jstring detParam, jstring detModel,
                                               jstring recParam, jstring recModel, jint threads) {
    auto e = std::make_unique<Engine>();
    configure(e->det, threads);
    configure(e->rec, threads);
    if (!load(e->det, env, detParam, detModel) || !load(e->rec, env, recParam, recModel)) return 0;
    return reinterpret_cast<jlong>(e.release());
}

JNIEXPORT void JNICALL
Java_io_github_lenkr0_verba_ocr_OcrNative_release(JNIEnv*, jclass, jlong handle) {
    delete engine(handle);
}

// For the bitmap scaled to width x height: per pixel, the probability that it is text (row by row).
JNIEXPORT jfloatArray JNICALL
Java_io_github_lenkr0_verba_ocr_OcrNative_detect(JNIEnv* env, jclass, jlong handle, jobject bitmap,
                                                 jint width, jint height) {
    ncnn::Mat in = ncnn::Mat::from_android_bitmap_resize(env, bitmap, ncnn::Mat::PIXEL_BGR, width, height);
    if (in.empty()) return nullptr;
    in.substract_mean_normalize(kMean, kNorm);
    ncnn::Extractor ex = engine(handle)->det.create_extractor();
    ex.input("in0", in);
    ncnn::Mat out;
    if (ex.extract("out0", out) != 0 || out.w != width || out.h != height) return nullptr;
    jfloatArray result = env->NewFloatArray(width * height);
    if (result) env->SetFloatArrayRegion(result, 0, width * height, out.channel(0));
    return result;
}

// Reads one text line: the bitmap region that [matrix] maps onto width x 48 pixels.
// Returns, per time step, the most likely character class and its probability:
// [class, probability, class, probability, ...]; class 0 is the CTC blank.
JNIEXPORT jfloatArray JNICALL
Java_io_github_lenkr0_verba_ocr_OcrNative_recognize(JNIEnv* env, jclass, jlong handle, jobject bitmap,
                                                    jfloatArray matrix, jint width) {
    if (width <= 0 || env->GetArrayLength(matrix) != 6) return nullptr;
    float m[6];
    env->GetFloatArrayRegion(matrix, 0, 6, m);
    std::vector<unsigned char> line(static_cast<size_t>(width) * kLineHeight * 4);
    {
        LockedBitmap src(env, bitmap);
        if (!src.pixels) return nullptr;
        warp(src, m, width, kLineHeight, line.data());
    }
    ncnn::Mat in = ncnn::Mat::from_pixels(line.data(), ncnn::Mat::PIXEL_RGBA2BGR, width, kLineHeight);
    in.substract_mean_normalize(kMean, kNorm);
    if (width < kMinLineWidth) {
        ncnn::Mat padded;
        ncnn::copy_make_border(in, padded, 0, 0, 0, kMinLineWidth - width, ncnn::BORDER_CONSTANT, 0.f);
        in = padded;
    }
    ncnn::Extractor ex = engine(handle)->rec.create_extractor();
    ex.input("in0", in);
    ncnn::Mat out;
    if (ex.extract("out0", out) != 0 || out.dims != 2) return nullptr;
    // out: h = time steps, w = character classes (softmax already applied)
    std::vector<float> best(static_cast<size_t>(out.h) * 2);
    for (int t = 0; t < out.h; t++) {
        const float* row = out.row(t);
        const float* top = std::max_element(row, row + out.w);
        best[t * 2] = static_cast<float>(top - row);
        best[t * 2 + 1] = *top;
    }
    jfloatArray result = env->NewFloatArray(static_cast<jsize>(best.size()));
    if (result) env->SetFloatArrayRegion(result, 0, static_cast<jsize>(best.size()), best.data());
    return result;
}

}  // extern "C"
