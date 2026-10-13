#include "plna/c_api.h"

#include <cstdio>
#include <cstdlib>
#include <string>

static const char* opName(int op) {
    switch (op) {
        case 1: return "Floor";
        case 2: return "Box2D";
        case 3: return "Line";
        case 4: return "Circle";
        case 5: return "Box3D";
        case 6: return "Path";
        case 7: return "Quad";
        default: return "?";
    }
}

int main(int argc, char** argv) {
    if (argc < 2) {
        std::fprintf(stderr, "Usage: %s <store_map.json> [--route fx fy tx ty]\n", argv[0]);
        return 1;
    }

    char err[512];
    if (!plna_map_load_file(argv[1], err, sizeof(err))) {
        std::fprintf(stderr, "load failed: %s\n", err);
        return 2;
    }

    uint32_t eq = 0, walls = 0, markers = 0, routePts = 0;
    uint64_t rev = 0;
    plna_map_stats(&eq, &walls, &markers, &routePts, &rev);
    float fw = 0, fh = 0;
    plna_map_floor_size(&fw, &fh);

    std::printf("PLNA %s\n", plna_core_version());
    std::printf("floor %.1f x %.1f m | equipment=%u walls=%u markers=%u rev=%llu\n",
                fw, fh, eq, walls, markers, static_cast<unsigned long long>(rev));

    if (argc >= 7 && std::string(argv[2]) == "--route") {
        float path[64];
        const size_t n = plna_map_route(
                std::strtof(argv[3], nullptr),
                std::strtof(argv[4], nullptr),
                std::strtof(argv[5], nullptr),
                std::strtof(argv[6], nullptr),
                path, 64, 1);
        std::printf("route floats=%zu:", n);
        for (size_t i = 0; i + 1 < n && i < 64; i += 2) {
            std::printf(" (%.2f,%.2f)", path[i], path[i + 1]);
        }
        std::printf("\n");
    }

    size_t count = 0;
    const float* dl = plna_map_build_draw_list(&count);
    std::printf("draw_list floats=%zu\n", count);

    size_t i = 0;
    int ops = 0;
    while (i < count) {
        const int op = static_cast<int>(dl[i++]);
        size_t need = 0;
        switch (op) {
            case 1: need = 8; break;
            case 2: need = 8; break;
            case 3: need = 9; break;
            case 4: need = 7; break;
            case 5: need = 9; break;
            case 6: {
                if (i >= count) break;
                const int pts = static_cast<int>(dl[i++]);
                need = static_cast<size_t>(pts) * 2 + 5;
                break;
            }
            case 7: need = 12; break;
            default:
                std::printf("  @%zu unknown op %d — stop\n", i - 1, op);
                return 3;
        }
        if (i + need > count) {
            std::printf("  truncated op %s\n", opName(op));
            break;
        }
        ++ops;
        if (ops <= 12 || op == 6 || op == 7) {
            std::printf("  %s", opName(op));
            for (size_t k = 0; k < need && k < 8; ++k) std::printf(" %.2f", dl[i + k]);
            if (need > 8) std::printf(" …");
            std::printf("\n");
        }
        i += need;
    }
    std::printf("ops counted≈%d (showing first/interesting)\n", ops);
    return 0;
}
