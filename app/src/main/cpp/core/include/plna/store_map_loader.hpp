#pragma once

#include <cstdint>
#include <string>
#include <string_view>

namespace plna::map {

struct LoadResult {
    bool ok = false;
    std::string error;
    uint32_t equipment = 0;
    uint32_t walls = 0;
    uint32_t markers = 0;
};

/**
 * Load a Firebase-style store_maps object (or full RTDB subtree containing it)
 * into MapScene::instance(). Clears the scene first on success path start.
 *
 * Accepts:
 * - bare store_maps object: { layout, walls, racks, coolers, stands, markers }
 * - wrapper with one child map id
 * - full export fragment under stores/.../store_maps/...
 */
LoadResult loadStoreMapJson(std::string_view json);

LoadResult loadStoreMapFile(const std::string& path);

} // namespace plna::map
