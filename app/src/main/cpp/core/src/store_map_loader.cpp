#include "plna/store_map_loader.hpp"

#include "map_scene.hpp"
#include "plna/color.hpp"

#include <nlohmann/json.hpp>

#include <algorithm>
#include <fstream>
#include <sstream>

namespace plna::map {

namespace {

using json = nlohmann::json;

float num(const json& j, const char* key, float fallback = 0.f) {
    if (!j.contains(key) || j[key].is_null()) return fallback;
    if (j[key].is_number()) return j[key].get<float>();
    return fallback;
}

std::string str(const json& j, const char* key, const std::string& fallback = {}) {
    if (!j.contains(key) || j[key].is_null()) return fallback;
    if (j[key].is_string()) return j[key].get<std::string>();
    return fallback;
}

EquipType equipTypeFrom(const std::string& raw, EquipType fallback) {
    if (raw == "rack") return EquipType::Rack;
    if (raw == "cooler") return EquipType::Cooler;
    if (raw == "stand") return EquipType::Stand;
    return fallback;
}

MarkerType markerTypeFrom(const std::string& raw) {
    if (raw == "beacon") return MarkerType::Beacon;
    if (raw == "cash" || raw == "cash_register") return MarkerType::Cash;
    if (raw == "entrance") return MarkerType::Entrance;
    if (raw == "exit") return MarkerType::Exit;
    return MarkerType::Other;
}

void applyColor(Equipment& e, const json& item) {
    const auto color = str(item, "zoneColor", str(item, "color"));
    if (!color.empty()) {
        const auto rgba = plna::parseCssColor(color);
        e.r = rgba.r;
        e.g = rgba.g;
        e.b = rgba.b;
        e.a = rgba.a;
    }
}

void applyColor(Marker& m, const json& item) {
    const auto color = str(item, "color", str(item, "zoneColor"));
    if (!color.empty()) {
        const auto rgba = plna::parseCssColor(color);
        m.r = rgba.r;
        m.g = rgba.g;
        m.b = rgba.b;
        m.a = rgba.a;
    }
}

Equipment parseEquipment(const json& item, EquipType fallbackType) {
    Equipment e;
    e.id = str(item, "id");
    e.type = equipTypeFrom(str(item, "equipmentType"), fallbackType);
    e.x = num(item, "posX", num(item, "rx"));
    e.y = num(item, "posY", num(item, "ry"));
    e.w = num(item, "widthMeters", 1.f);
    e.d = num(item, "lengthMeters", 0.7f);
    e.h = num(item, "heightMeters", 1.6f);
    e.rotationDeg = num(item, "rotation");
    e.shelves = static_cast<int>(num(item, "shelfCount", 4.f));
    if (item.contains("levels") && item["levels"].is_array()) {
        e.shelves = std::max(e.shelves, static_cast<int>(item["levels"].size()));
    }
    applyColor(e, item);
    return e;
}

Marker parseMarker(const json& item) {
    Marker m;
    m.id = str(item, "id", str(item, "code"));
    m.type = markerTypeFrom(str(item, "type"));
    m.x = num(item, "posX");
    m.y = num(item, "posY");
    m.w = num(item, "widthMeters", 0.4f);
    m.d = num(item, "depthMeters", 0.4f);
    m.rotationDeg = num(item, "rotation");
    applyColor(m, item);
    return m;
}

Wall parseWall(const json& item) {
    Wall w;
    w.x1 = num(item, "startX");
    w.y1 = num(item, "startY");
    w.x2 = num(item, "endX");
    w.y2 = num(item, "endY");
    const auto wt = str(item, "wallType");
    w.isDoor = (wt == "door") || item.value("isDoor", false);
    return w;
}

bool looksLikeStoreMap(const json& j) {
    return j.is_object() && (
            j.contains("layout") || j.contains("racks") || j.contains("walls") ||
            j.contains("coolers") || j.contains("stands") || j.contains("markers"));
}

const json* findStoreMapNode(const json& root) {
    if (looksLikeStoreMap(root)) return &root;

    if (root.contains("store_maps") && root["store_maps"].is_object()) {
        for (auto it = root["store_maps"].begin(); it != root["store_maps"].end(); ++it) {
            if (looksLikeStoreMap(it.value())) return &it.value();
        }
    }

    // stores/{id}/store_maps/{id}
    if (root.contains("stores") && root["stores"].is_object()) {
        for (auto& [_, store] : root["stores"].items()) {
            if (!store.is_object() || !store.contains("store_maps")) continue;
            for (auto& [__, mapNode] : store["store_maps"].items()) {
                if (looksLikeStoreMap(mapNode)) return &mapNode;
            }
        }
    }

    // single-key wrapper { "gippo-...": { layout... } }
    if (root.is_object() && root.size() == 1) {
        const auto& only = root.begin().value();
        if (looksLikeStoreMap(only)) return &only;
        if (only.is_object() && only.contains("store_maps")) {
            return findStoreMapNode(only);
        }
    }

    return nullptr;
}

LoadResult applyStoreMap(const json& map) {
    LoadResult result;
    auto& scene = MapScene::instance();
    scene.clear();

    if (map.contains("layout") && map["layout"].is_object()) {
        const auto& layout = map["layout"];
        scene.setFloor(
                num(layout, "widthMeters", 40.f),
                num(layout, "heightMeters", 40.f)
        );
        // layout.gridSizeCm is the snap cell; draw a coarser major grid (~5 m)
        // so Canvas is not flooded with lines (CPU/GPU + battery).
        const float cellM = num(layout, "gridSizeCm", 500.f) / 100.f;
        if (cellM <= 0.f) {
            scene.setGridStepMeters(0.f);
        } else {
            const float major = std::max(5.f, std::round(5.f / cellM) * cellM);
            scene.setGridStepMeters(major);
        }
    }

    auto loadArray = [&](const char* key, auto fn) {
        if (!map.contains(key) || !map[key].is_array()) return;
        for (const auto& item : map[key]) {
            if (!item.is_object()) continue;
            fn(item);
        }
    };

    loadArray("walls", [&](const json& item) {
        scene.addWall(parseWall(item));
        ++result.walls;
    });

    loadArray("racks", [&](const json& item) {
        scene.addEquipment(parseEquipment(item, EquipType::Rack));
        ++result.equipment;
    });
    loadArray("coolers", [&](const json& item) {
        scene.addEquipment(parseEquipment(item, EquipType::Cooler));
        ++result.equipment;
    });
    loadArray("stands", [&](const json& item) {
        scene.addEquipment(parseEquipment(item, EquipType::Stand));
        ++result.equipment;
    });
    loadArray("markers", [&](const json& item) {
        scene.addMarker(parseMarker(item));
        ++result.markers;
    });

    result.ok = true;
    return result;
}

} // namespace

LoadResult loadStoreMapJson(std::string_view jsonText) {
    LoadResult result;
    json root;
    try {
        root = json::parse(jsonText);
    } catch (const json::exception& ex) {
        result.error = std::string("JSON parse: ") + ex.what();
        return result;
    }

    const json* map = findStoreMapNode(root);
    if (!map) {
        result.error = "No store_maps object found (need layout/racks/walls/...)";
        return result;
    }

    try {
        result = applyStoreMap(*map);
    } catch (const std::exception& ex) {
        result.ok = false;
        result.error = ex.what();
        MapScene::instance().clear();
    }
    return result;
}

LoadResult loadStoreMapFile(const std::string& path) {
    std::ifstream in(path);
    if (!in) {
        LoadResult r;
        r.error = "Cannot open file: " + path;
        return r;
    }
    std::ostringstream ss;
    ss << in.rdbuf();
    return loadStoreMapJson(ss.str());
}

} // namespace plna::map
