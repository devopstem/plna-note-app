#include "map_scene.hpp"

#include <algorithm>
#include <cmath>

namespace plna::map {

namespace {

constexpr float kPi = 3.14159265358979323846f;
constexpr float kRotEps = 0.05f;

/** True only near 0°/360° — then SW-origin Box2D/Box3D matches Firebase footprint. */
bool nearlyZeroRotation(float deg) {
    float a = std::fmod(std::fabs(deg), 360.f);
    if (a > 180.f) a = 360.f - a;
    return a < kRotEps;
}

/** Corners of rectangle with SW origin (x,y), size (w,d), rotation around center. */
void footprintCorners(float x, float y, float w, float d, float rotDeg, float out[4][2]) {
    const float cx = x + w * 0.5f;
    const float cy = y + d * 0.5f;
    const float rad = rotDeg * (kPi / 180.f);
    const float c = std::cos(rad);
    const float s = std::sin(rad);
    const float local[4][2] = {
            {-w * 0.5f, -d * 0.5f},
            {w * 0.5f, -d * 0.5f},
            {w * 0.5f, d * 0.5f},
            {-w * 0.5f, d * 0.5f},
    };
    for (int i = 0; i < 4; ++i) {
        out[i][0] = cx + local[i][0] * c - local[i][1] * s;
        out[i][1] = cy + local[i][0] * s + local[i][1] * c;
    }
}

void aabbOfCorners(const float corners[4][2], float& minX, float& minY, float& maxX, float& maxY) {
    minX = maxX = corners[0][0];
    minY = maxY = corners[0][1];
    for (int i = 1; i < 4; ++i) {
        minX = std::min(minX, corners[i][0]);
        maxX = std::max(maxX, corners[i][0]);
        minY = std::min(minY, corners[i][1]);
        maxY = std::max(maxY, corners[i][1]);
    }
}

bool layerOk(EquipType t, bool racks, bool coolers, bool stands) {
    switch (t) {
        case EquipType::Rack: return racks;
        case EquipType::Cooler: return coolers;
        case EquipType::Stand: return stands;
    }
    return true;
}

void pushFloor(std::vector<float>& out, float w, float h) {
    out.push_back(static_cast<float>(DrawOp::Floor));
    out.push_back(0.f);
    out.push_back(0.f);
    out.push_back(w);
    out.push_back(h);
    out.push_back(0.06f);
    out.push_back(0.09f);
    out.push_back(0.16f);
    out.push_back(1.f);
}

void pushBox2d(std::vector<float>& out, float x, float y, float w, float d,
               float r, float g, float b, float a) {
    out.push_back(static_cast<float>(DrawOp::Box2D));
    out.push_back(x);
    out.push_back(y);
    out.push_back(w);
    out.push_back(d);
    out.push_back(r);
    out.push_back(g);
    out.push_back(b);
    out.push_back(a);
}

void pushBox3d(std::vector<float>& out, float x, float y, float w, float d, float h,
               float r, float g, float b, float a) {
    out.push_back(static_cast<float>(DrawOp::Box3D));
    out.push_back(x);
    out.push_back(y);
    out.push_back(w);
    out.push_back(d);
    out.push_back(h);
    out.push_back(r);
    out.push_back(g);
    out.push_back(b);
    out.push_back(a);
}

void pushQuad(std::vector<float>& out, const float corners[4][2],
              float r, float g, float b, float a) {
    out.push_back(static_cast<float>(DrawOp::Quad));
    for (int i = 0; i < 4; ++i) {
        out.push_back(corners[i][0]);
        out.push_back(corners[i][1]);
    }
    out.push_back(r);
    out.push_back(g);
    out.push_back(b);
    out.push_back(a);
}

void pushLine(std::vector<float>& out, float x1, float y1, float x2, float y2,
              float r, float g, float b, float a, float stroke) {
    out.push_back(static_cast<float>(DrawOp::Line));
    out.push_back(x1);
    out.push_back(y1);
    out.push_back(x2);
    out.push_back(y2);
    out.push_back(r);
    out.push_back(g);
    out.push_back(b);
    out.push_back(a);
    out.push_back(stroke);
}

void pushCircle(std::vector<float>& out, float x, float y, float radius,
                float r, float g, float b, float a) {
    out.push_back(static_cast<float>(DrawOp::Circle));
    out.push_back(x);
    out.push_back(y);
    out.push_back(radius);
    out.push_back(r);
    out.push_back(g);
    out.push_back(b);
    out.push_back(a);
}

bool segmentHitsAabb(float x1, float y1, float x2, float y2,
                     float minX, float minY, float maxX, float maxY) {
    const float segMinX = std::min(x1, x2);
    const float segMaxX = std::max(x1, x2);
    const float segMinY = std::min(y1, y2);
    const float segMaxY = std::max(y1, y2);
    if (segMaxX < minX || segMinX > maxX || segMaxY < minY || segMinY > maxY) {
        return false;
    }
    // Axis-aligned segment cases (our L-path only uses H/V legs).
    if (std::fabs(y1 - y2) < 1e-4f) {
        return y1 >= minY && y1 <= maxY && segMaxX >= minX && segMinX <= maxX;
    }
    if (std::fabs(x1 - x2) < 1e-4f) {
        return x1 >= minX && x1 <= maxX && segMaxY >= minY && segMinY <= maxY;
    }
    return true;
}

} // namespace

MapScene& MapScene::instance() {
    static MapScene scene;
    return scene;
}

void MapScene::touch() {
    ++revision_;
}

void MapScene::clear() {
    equipment_.clear();
    walls_.clear();
    markers_.clear();
    route_.clear();
    floorW_ = 40.f;
    floorH_ = 40.f;
    gridStep_ = 5.f;
    touch();
}

void MapScene::setFloor(float widthMeters, float heightMeters) {
    floorW_ = std::max(1.f, widthMeters);
    floorH_ = std::max(1.f, heightMeters);
    touch();
}

void MapScene::setGridStepMeters(float meters) {
    gridStep_ = std::max(0.f, meters);
    touch();
}

void MapScene::addEquipment(const Equipment& e) {
    equipment_.push_back(e);
    touch();
}

void MapScene::addWall(const Wall& w) {
    walls_.push_back(w);
    touch();
}

void MapScene::addMarker(const Marker& m) {
    markers_.push_back(m);
    touch();
}

void MapScene::setLayerVisible(int layer, bool visible) {
    switch (layer) {
        case 1: showRacks_ = visible; break;
        case 2: showCoolers_ = visible; break;
        case 3: showStands_ = visible; break;
        case 4: showBeacons_ = visible; break;
        default: return;
    }
    touch();
}

void MapScene::setView3D(bool enabled) {
    if (view3d_ == enabled) return;
    view3d_ = enabled;
    touch();
}

void MapScene::setRoute(const std::vector<float>& xyPairs) {
    route_ = xyPairs;
    touch();
}

void MapScene::clearRoute() {
    if (route_.empty()) return;
    route_.clear();
    touch();
}

SceneStats MapScene::stats() const {
    return SceneStats{
            static_cast<uint32_t>(equipment_.size()),
            static_cast<uint32_t>(walls_.size()),
            static_cast<uint32_t>(markers_.size()),
            static_cast<uint32_t>(route_.size() / 2),
            revision_,
            floorW_,
            floorH_,
            gridStep_,
    };
}

void MapScene::rebuildDrawList() const {
    cachedDraw_.clear();
    cachedDraw_.reserve(2048);

    pushFloor(cachedDraw_, floorW_, floorH_);

    if (gridStep_ > 0.f) {
        for (float x = gridStep_; x < floorW_; x += gridStep_) {
            pushLine(cachedDraw_, x, 0.f, x, floorH_, 0.15f, 0.2f, 0.3f, 0.35f, 1.f);
        }
        for (float y = gridStep_; y < floorH_; y += gridStep_) {
            pushLine(cachedDraw_, 0.f, y, floorW_, y, 0.15f, 0.2f, 0.3f, 0.35f, 1.f);
        }
    }

    for (const auto& w : walls_) {
        if (w.isDoor) {
            pushLine(cachedDraw_, w.x1, w.y1, w.x2, w.y2, 0.13f, 0.77f, 0.37f, 0.95f, 3.f);
        } else {
            pushLine(cachedDraw_, w.x1, w.y1, w.x2, w.y2, 0.7f, 0.75f, 0.85f, 0.95f, 2.5f);
        }
    }

    for (const auto& e : equipment_) {
        if (!e.visible) continue;
        if (!layerOk(e.type, showRacks_, showCoolers_, showStands_)) continue;

        const bool rotated = !nearlyZeroRotation(e.rotationDeg);
        if (rotated) {
            float corners[4][2];
            footprintCorners(e.x, e.y, e.w, e.d, e.rotationDeg, corners);
            pushQuad(cachedDraw_, corners, e.r, e.g, e.b, e.a);
        } else if (view3d_) {
            pushBox3d(cachedDraw_, e.x, e.y, e.w, e.d, e.h, e.r, e.g, e.b, e.a);
        } else {
            pushBox2d(cachedDraw_, e.x, e.y, e.w, e.d, e.r, e.g, e.b, e.a);
        }
    }

    for (const auto& m : markers_) {
        if (!m.visible) continue;
        if (m.type == MarkerType::Beacon && !showBeacons_) continue;

        const bool rotated = !nearlyZeroRotation(m.rotationDeg);
        if (m.type == MarkerType::Beacon) {
            const float rad = std::max(m.w, m.d) * 0.5f;
            pushCircle(cachedDraw_, m.x, m.y, rad, m.r, m.g, m.b, m.a);
        } else if (rotated) {
            float corners[4][2];
            footprintCorners(m.x, m.y, m.w, m.d, m.rotationDeg, corners);
            pushQuad(cachedDraw_, corners, m.r, m.g, m.b, m.a);
        } else {
            pushBox2d(cachedDraw_, m.x, m.y, m.w, m.d, m.r, m.g, m.b, m.a);
        }
    }

    if (route_.size() >= 4) {
        cachedDraw_.push_back(static_cast<float>(DrawOp::Path));
        const int count = static_cast<int>(route_.size() / 2);
        cachedDraw_.push_back(static_cast<float>(count));
        cachedDraw_.insert(cachedDraw_.end(), route_.begin(), route_.end());
        // тонкий маршрут — стрелки/стиль рисует Android StoreMapView
        cachedDraw_.push_back(0.15f);
        cachedDraw_.push_back(0.35f);
        cachedDraw_.push_back(0.95f);
        cachedDraw_.push_back(1.f);
        cachedDraw_.push_back(2.f);
    }

    cachedRevision_ = revision_;
}

const std::vector<float>& MapScene::buildDrawList() const {
    if (cachedRevision_ != revision_) {
        rebuildDrawList();
    }
    return cachedDraw_;
}

std::vector<float> MapScene::routeAround(float fromX, float fromY, float toX, float toY) const {
    std::vector<float> path;
    path.reserve(8);
    path.push_back(fromX);
    path.push_back(fromY);

    const float midX = toX;
    const float midY = fromY;

    auto blockedByEquipment = [&](float x1, float y1, float x2, float y2) {
        for (const auto& e : equipment_) {
            if (!e.visible) continue;
            float corners[4][2];
            footprintCorners(e.x, e.y, e.w, e.d, e.rotationDeg, corners);
            float minX, minY, maxX, maxY;
            aabbOfCorners(corners, minX, minY, maxX, maxY);
            if (segmentHitsAabb(x1, y1, x2, y2, minX, minY, maxX, maxY)) {
                return true;
            }
        }
        return false;
    };

    const bool blockedH = blockedByEquipment(fromX, fromY, midX, midY);
    if (blockedH) {
        const float detourY = std::clamp(fromY + 3.f, 0.f, floorH_);
        path.push_back(fromX);
        path.push_back(detourY);
        path.push_back(toX);
        path.push_back(detourY);
    } else {
        path.push_back(midX);
        path.push_back(midY);
        if (blockedByEquipment(midX, midY, toX, toY)) {
            const float detourX = std::clamp(fromX + 3.f, 0.f, floorW_);
            path.clear();
            path.push_back(fromX);
            path.push_back(fromY);
            path.push_back(detourX);
            path.push_back(fromY);
            path.push_back(detourX);
            path.push_back(toY);
        }
    }
    path.push_back(toX);
    path.push_back(toY);
    return path;
}

} // namespace plna::map
