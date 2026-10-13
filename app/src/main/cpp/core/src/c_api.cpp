#include "plna/c_api.h"

#include "map_geometry.hpp"
#include "map_scene.hpp"
#include "plna/store_map_loader.hpp"

#include <algorithm>
#include <cstring>
#include <string>

namespace {

void setError(char* errbuf, size_t errbuf_len, const std::string& msg) {
    if (!errbuf || errbuf_len == 0) return;
    if (msg.empty()) {
        errbuf[0] = '\0';
        return;
    }
    std::strncpy(errbuf, msg.c_str(), errbuf_len - 1);
    errbuf[errbuf_len - 1] = '\0';
}

} // namespace

extern "C" {

const char* plna_core_version(void) {
    return "PLNA Native Core v1.1";
}

void plna_map_clear(void) {
    plna::map::MapScene::instance().clear();
}

void plna_map_set_floor(float width_m, float height_m) {
    plna::map::MapScene::instance().setFloor(width_m, height_m);
}

void plna_map_set_grid_step_meters(float meters) {
    plna::map::MapScene::instance().setGridStepMeters(meters);
}

void plna_map_set_view_3d(int enabled) {
    plna::map::MapScene::instance().setView3D(enabled != 0);
}

void plna_map_set_layer_visible(int layer, int visible) {
    plna::map::MapScene::instance().setLayerVisible(layer, visible != 0);
}

void plna_map_add_wall(float x1, float y1, float x2, float y2, int is_door) {
    plna::map::Wall w;
    w.x1 = x1;
    w.y1 = y1;
    w.x2 = x2;
    w.y2 = y2;
    w.isDoor = is_door != 0;
    plna::map::MapScene::instance().addWall(w);
}

void plna_map_add_equipment(
        const char* id,
        int type,
        float x, float y, float w, float d, float h,
        float rotation_deg,
        int shelves,
        float r, float g, float b, float a) {
    plna::map::Equipment e;
    e.id = id ? id : "";
    e.type = static_cast<plna::map::EquipType>(type);
    e.x = x;
    e.y = y;
    e.w = w;
    e.d = d;
    e.h = h;
    e.rotationDeg = rotation_deg;
    e.shelves = shelves;
    e.r = r;
    e.g = g;
    e.b = b;
    e.a = a;
    plna::map::MapScene::instance().addEquipment(e);
}

void plna_map_add_marker(
        const char* id,
        int type,
        float x, float y, float w, float d,
        float rotation_deg,
        float r, float g, float b, float a) {
    plna::map::Marker m;
    m.id = id ? id : "";
    m.type = static_cast<plna::map::MarkerType>(type);
    m.x = x;
    m.y = y;
    m.w = w;
    m.d = d;
    m.rotationDeg = rotation_deg;
    m.r = r;
    m.g = g;
    m.b = b;
    m.a = a;
    plna::map::MapScene::instance().addMarker(m);
}

int plna_map_load_json(const char* json_utf8, char* errbuf, size_t errbuf_len) {
    if (!json_utf8) {
        setError(errbuf, errbuf_len, "json is null");
        return 0;
    }
    auto result = plna::map::loadStoreMapJson(json_utf8);
    setError(errbuf, errbuf_len, result.error);
    return result.ok ? 1 : 0;
}

int plna_map_load_file(const char* path, char* errbuf, size_t errbuf_len) {
    if (!path) {
        setError(errbuf, errbuf_len, "path is null");
        return 0;
    }
    auto result = plna::map::loadStoreMapFile(path);
    setError(errbuf, errbuf_len, result.error);
    return result.ok ? 1 : 0;
}

uint64_t plna_map_revision(void) {
    return plna::map::MapScene::instance().revision();
}

const float* plna_map_build_draw_list(size_t* out_count) {
    const auto& list = plna::map::MapScene::instance().buildDrawList();
    if (out_count) *out_count = list.size();
    return list.empty() ? nullptr : list.data();
}

size_t plna_map_copy_draw_list(float* dst, size_t dst_floats) {
    const auto& list = plna::map::MapScene::instance().buildDrawList();
    if (!dst || dst_floats == 0) return list.size();
    const size_t n = std::min(dst_floats, list.size());
    if (n > 0) {
        std::memcpy(dst, list.data(), n * sizeof(float));
    }
    return list.size();
}

void plna_map_floor_size(float* out_w, float* out_h) {
    auto& s = plna::map::MapScene::instance();
    if (out_w) *out_w = s.floorWidth();
    if (out_h) *out_h = s.floorHeight();
}

void plna_map_stats(
        uint32_t* equipment,
        uint32_t* walls,
        uint32_t* markers,
        uint32_t* route_points,
        uint64_t* revision) {
    const auto st = plna::map::MapScene::instance().stats();
    if (equipment) *equipment = st.equipment;
    if (walls) *walls = st.walls;
    if (markers) *markers = st.markers;
    if (route_points) *route_points = st.routePoints;
    if (revision) *revision = st.revision;
}

size_t plna_map_route(
        float from_x, float from_y, float to_x, float to_y,
        float* out_xy, size_t max_floats,
        int apply_to_scene) {
    auto path = plna::map::MapScene::instance().routeAround(from_x, from_y, to_x, to_y);
    if (apply_to_scene) {
        plna::map::MapScene::instance().setRoute(path);
    }
    if (out_xy && max_floats > 0) {
        const size_t n = std::min(max_floats, path.size());
        std::memcpy(out_xy, path.data(), n * sizeof(float));
    }
    return path.size();
}

void plna_map_clear_route(void) {
    plna::map::MapScene::instance().clearRoute();
}

void plna_product_put(const char* barcode, float x, float y, float z, float shelf) {
    plna::math::putProduct(barcode ? barcode : "", x, y, z, shelf);
}

int plna_product_find(const char* barcode, float* out_xyzw) {
    auto hit = plna::math::findProduct(barcode ? barcode : "");
    if (!hit.found) return 0;
    if (out_xyzw) {
        out_xyzw[0] = hit.x;
        out_xyzw[1] = hit.y;
        out_xyzw[2] = hit.z;
        out_xyzw[3] = hit.shelf;
    }
    return 1;
}

void plna_product_clear(void) {
    plna::math::clearProducts();
}

} // extern "C"
