#include <jni.h>

#include <string>
#include <vector>

#include "map_geometry.hpp"
#include "map_scene.hpp"
#include "plna/store_map_loader.hpp"

namespace {

jfloatArray toJavaFloatArray(JNIEnv* env, const std::vector<float>& data) {
    jfloatArray arr = env->NewFloatArray(static_cast<jsize>(data.size()));
    if (!arr || data.empty()) return arr;
    env->SetFloatArrayRegion(arr, 0, static_cast<jsize>(data.size()), data.data());
    return arr;
}

std::string jstringToStd(JNIEnv* env, jstring value) {
    if (!value) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    std::string out = chars ? chars : "";
    if (chars) env->ReleaseStringUTFChars(value, chars);
    return out;
}

}
extern "C" JNIEXPORT jfloatArray JNICALL
Java_by_smartplanogram_plnanote_nativebridge_NativeMapEngine_calculateRoute(
        JNIEnv* env,
        jclass,
        jfloat fromX,
        jfloat fromY,
        jfloat toX,
        jfloat toY) {
    auto path = plna::math::calculateRoute(fromX, fromY, toX, toY);
    return toJavaFloatArray(env, path);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_by_smartplanogram_plnanote_nativebridge_NativeMapEngine_drawRack(
        JNIEnv* env,
        jclass,
        jstring rackId,
        jfloat startX,
        jfloat startY,
        jfloat width,
        jfloat depth,
        jint shelves,
        jfloat shelfHeight) {
    auto id = jstringToStd(env, rackId);
    auto verts = plna::math::drawRack(id, startX, startY, width, depth, shelves, shelfHeight);
    return toJavaFloatArray(env, verts);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_by_smartplanogram_plnanote_nativebridge_NativeMapEngine_findProduct(
        JNIEnv* env,
        jclass,
        jstring barcode) {
    auto code = jstringToStd(env, barcode);
    auto hit = plna::math::findProduct(code);
    if (!hit.found) {
        return env->NewFloatArray(0);
    }
    std::vector<float> data = {hit.x, hit.y, hit.z, hit.shelf};
    return toJavaFloatArray(env, data);
}

extern "C" JNIEXPORT jstring JNICALL
Java_by_smartplanogram_plnanote_nativebridge_NativeMapEngine_getCoreVersion(
        JNIEnv* env,
        jclass) {
    return env->NewStringUTF("PLNA Native Core v1.0");
}

extern "C" JNIEXPORT void JNICALL
Java_by_smartplanogram_plnanote_nativebridge_NativeMapEngine_nativeClearScene(
        JNIEnv*,
        jclass) {
    plna::map::MapScene::instance().clear();
    plna::math::clearProducts();
}

extern "C" JNIEXPORT void JNICALL
Java_by_smartplanogram_plnanote_nativebridge_NativeMapEngine_nativeSetFloor(
        JNIEnv*,
        jclass,
        jfloat widthMeters,
        jfloat heightMeters) {
    plna::map::MapScene::instance().setFloor(widthMeters, heightMeters);
}

extern "C" JNIEXPORT void JNICALL
Java_by_smartplanogram_plnanote_nativebridge_NativeMapEngine_nativeAddEquipment(
        JNIEnv* env,
        jclass,
        jstring id,
        jint type,
        jfloat x,
        jfloat y,
        jfloat w,
        jfloat d,
        jfloat h,
        jfloat rotationDeg,
        jint shelves,
        jfloat r,
        jfloat g,
        jfloat b,
        jfloat a) {
    plna::map::Equipment e;
    e.id = jstringToStd(env, id);
    e.type = static_cast<plna::map::EquipType>(type);
    e.x = x;
    e.y = y;
    e.w = w;
    e.d = d;
    e.h = h;
    e.rotationDeg = rotationDeg;
    e.shelves = shelves;
    e.r = r;
    e.g = g;
    e.b = b;
    e.a = a;
    plna::map::MapScene::instance().addEquipment(e);
}

extern "C" JNIEXPORT void JNICALL
Java_by_smartplanogram_plnanote_nativebridge_NativeMapEngine_nativeAddWall(
        JNIEnv*,
        jclass,
        jfloat x1,
        jfloat y1,
        jfloat x2,
        jfloat y2,
        jboolean isDoor) {
    plna::map::Wall w;
    w.x1 = x1;
    w.y1 = y1;
    w.x2 = x2;
    w.y2 = y2;
    w.isDoor = isDoor == JNI_TRUE;
    plna::map::MapScene::instance().addWall(w);
}

extern "C" JNIEXPORT void JNICALL
Java_by_smartplanogram_plnanote_nativebridge_NativeMapEngine_nativeAddMarker(
        JNIEnv* env,
        jclass,
        jstring id,
        jint type,
        jfloat x,
        jfloat y,
        jfloat w,
        jfloat d,
        jfloat r,
        jfloat g,
        jfloat b,
        jfloat a) {
    plna::map::Marker m;
    m.id = jstringToStd(env, id);
    m.type = static_cast<plna::map::MarkerType>(type);
    m.x = x;
    m.y = y;
    m.w = w;
    m.d = d;
    m.r = r;
    m.g = g;
    m.b = b;
    m.a = a;
    plna::map::MapScene::instance().addMarker(m);
}

extern "C" JNIEXPORT void JNICALL
Java_by_smartplanogram_plnanote_nativebridge_NativeMapEngine_nativeSetLayerVisible(
        JNIEnv*,
        jclass,
        jint layer,
        jboolean visible) {
    plna::map::MapScene::instance().setLayerVisible(layer, visible == JNI_TRUE);
}

extern "C" JNIEXPORT void JNICALL
Java_by_smartplanogram_plnanote_nativebridge_NativeMapEngine_nativeSetView3D(
        JNIEnv*,
        jclass,
        jboolean enabled) {
    plna::map::MapScene::instance().setView3D(enabled == JNI_TRUE);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_by_smartplanogram_plnanote_nativebridge_NativeMapEngine_nativeBuildDrawList(
        JNIEnv* env,
        jclass) {
    const auto& list = plna::map::MapScene::instance().buildDrawList();
    return toJavaFloatArray(env, list);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_by_smartplanogram_plnanote_nativebridge_NativeMapEngine_nativeLoadStoreMapJson(
        JNIEnv* env,
        jclass,
        jstring json) {
    if (!json) return JNI_FALSE;
    const auto text = jstringToStd(env, json);
    const auto result = plna::map::loadStoreMapJson(text);
    return result.ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jlong JNICALL
Java_by_smartplanogram_plnanote_nativebridge_NativeMapEngine_nativeRevision(
        JNIEnv*,
        jclass) {
    return static_cast<jlong>(plna::map::MapScene::instance().revision());
}

extern "C" JNIEXPORT void JNICALL
Java_by_smartplanogram_plnanote_nativebridge_NativeMapEngine_nativePutProduct(
        JNIEnv* env,
        jclass,
        jstring barcode,
        jfloat x,
        jfloat y,
        jfloat z,
        jfloat shelf) {
    plna::math::putProduct(jstringToStd(env, barcode), x, y, z, shelf);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_by_smartplanogram_plnanote_nativebridge_NativeMapEngine_nativeFloorSize(
        JNIEnv* env,
        jclass) {
    auto& s = plna::map::MapScene::instance();
    std::vector<float> data = {s.floorWidth(), s.floorHeight()};
    return toJavaFloatArray(env, data);
}
