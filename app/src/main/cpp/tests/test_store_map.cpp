#include "plna/c_api.h"
#include "plna/color.hpp"

#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <string>

static int g_fails = 0;

#define CHECK(cond) do { \
    if (!(cond)) { \
        std::fprintf(stderr, "FAIL %s:%d: %s\n", __FILE__, __LINE__, #cond); \
        ++g_fails; \
    } \
} while (0)

int main(int argc, char** argv) {
    const char* fixture = argc > 1 ? argv[1] : "fixtures/store_map_sample.json";

    {
        auto c = plna::parseCssColor("#6366F1");
        CHECK(std::fabs(c.r - 99.f / 255.f) < 0.01f);
        CHECK(std::fabs(c.b - 241.f / 255.f) < 0.01f);
    }

    char err[256];
    CHECK(plna_map_load_file(fixture, err, sizeof(err)) == 1);

    uint32_t eq = 0, walls = 0, markers = 0, routePts = 0;
    uint64_t rev = 0;
    plna_map_stats(&eq, &walls, &markers, &routePts, &rev);
    CHECK(eq == 16);      // 10+3+3
    CHECK(walls == 6);
    CHECK(markers == 9);

    float w = 0, h = 0;
    plna_map_floor_size(&w, &h);
    CHECK(std::fabs(w - 30.f) < 0.01f);
    CHECK(std::fabs(h - 25.f) < 0.01f);

    const uint64_t rev1 = plna_map_revision();
    size_t n1 = 0;
    const float* d1 = plna_map_build_draw_list(&n1);
    CHECK(d1 != nullptr);
    CHECK(n1 > 20);
    CHECK(static_cast<int>(d1[0]) == 1); // DrawOp::Floor
    size_t n2 = 0;
    const float* d2 = plna_map_build_draw_list(&n2);
    CHECK(d1 == d2); // cache pointer stable until mutate
    CHECK(n1 == n2);
    CHECK(plna_map_revision() == rev1);

    float path[32];
    const size_t pn = plna_map_route(1.f, 1.f, 28.f, 20.f, path, 32, 1);
    CHECK(pn >= 4);
    CHECK(plna_map_revision() != rev1);

    plna_product_put("4810014018641", 2.f, 10.f, 0.4f, 2.f);
    float xyzw[4]{};
    CHECK(plna_product_find("4810014018641", xyzw) == 1);
    CHECK(std::fabs(xyzw[0] - 2.f) < 0.01f);
    CHECK(plna_product_find("nope", xyzw) == 0);

    if (g_fails) {
        std::fprintf(stderr, "%d checks failed\n", g_fails);
        return 1;
    }
    std::printf("test_store_map OK (%s)\n", fixture);
    return 0;
}
