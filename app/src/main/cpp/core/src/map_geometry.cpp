#include "map_geometry.hpp"
#include "map_scene.hpp"

#include <algorithm>
#include <span>
#include <unordered_map>

namespace plna::math {

namespace {

std::unordered_map<std::string, ProductHit>& productCache() {
    static std::unordered_map<std::string, ProductHit> cache;
    return cache;
}

} // namespace

std::vector<float> calculateRoute(float fromX, float fromY, float toX, float toY) {
    auto path = plna::map::MapScene::instance().routeAround(fromX, fromY, toX, toY);
    plna::map::MapScene::instance().setRoute(path);
    return path;
}

std::vector<float> drawRack(
        const std::string& /*rackId*/,
        float startX,
        float startY,
        float width,
        float depth,
        int shelves,
        float shelfHeight
) {
    const int shelfCount = std::max(1, shelves);
    std::vector<float> vertices;
    vertices.reserve(static_cast<size_t>(shelfCount) * 4 * 3);

    for (int s = 0; s < shelfCount; ++s) {
        const float y = static_cast<float>(s) * shelfHeight;
        const float corners[4][2] = {
                {startX, startY},
                {startX + width, startY},
                {startX + width, startY + depth},
                {startX, startY + depth},
        };
        for (const auto& c : corners) {
            vertices.push_back(c[0]);
            vertices.push_back(y);
            vertices.push_back(c[1]);
        }
    }
    return vertices;
}

ProductHit findProduct(const std::string& barcode) {
    const auto& cache = productCache();
    const auto it = cache.find(barcode);
    if (it == cache.end()) {
        return {0.f, 0.f, 0.f, 0.f, false};
    }
    return it->second;
}

void putProduct(const std::string& barcode, float x, float y, float z, float shelf) {
    productCache()[barcode] = {x, y, z, shelf, true};
}

void clearProducts() {
    productCache().clear();
}

std::vector<Vector3> calculateBatchPositions(
        const std::vector<float>& starts,
        const std::vector<float>& ends,
        const std::vector<int32_t>& shelves,
        float shelfHeight
) {
    size_t count = std::min({starts.size(), ends.size(), shelves.size()});
    if (count == 0) return {};

    std::vector<Vector3> result(count);
    std::span<const float> spanStarts(starts.data(), count);
    std::span<const float> spanEnds(ends.data(), count);
    std::span<const int32_t> spanShelves(shelves.data(), count);
    std::span<Vector3> spanResult(result.data(), count);

    for (size_t i = 0; i < count; ++i) {
        spanResult[i].x = (spanStarts[i] + spanEnds[i]) * 0.5f;
        spanResult[i].y = static_cast<float>(spanShelves[i]) * shelfHeight;
        spanResult[i].z = 0.05f;
    }
    return result;
}

} // namespace plna::math
