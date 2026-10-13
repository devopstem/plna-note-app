#pragma once

/**
 * Stable C ABI for Android (optional), iOS, Windows, desktop tools.
 * Link against plna_core. All map calls touch the process-wide MapScene singleton.
 */

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#if defined(_WIN32) && defined(PLNA_BUILD_SHARED)
#  ifdef PLNA_EXPORTS
#    define PLNA_API __declspec(dllexport)
#  else
#    define PLNA_API __declspec(dllimport)
#  endif
#else
#  define PLNA_API __attribute__((visibility("default")))
#endif

PLNA_API const char* plna_core_version(void);

PLNA_API void plna_map_clear(void);
PLNA_API void plna_map_set_floor(float width_m, float height_m);
PLNA_API void plna_map_set_grid_step_meters(float meters);
PLNA_API void plna_map_set_view_3d(int enabled);
PLNA_API void plna_map_set_layer_visible(int layer, int visible);

PLNA_API void plna_map_add_wall(float x1, float y1, float x2, float y2, int is_door);

PLNA_API void plna_map_add_equipment(
        const char* id,
        int type, /* 1 rack 2 cooler 3 stand */
        float x, float y, float w, float d, float h,
        float rotation_deg,
        int shelves,
        float r, float g, float b, float a);

PLNA_API void plna_map_add_marker(
        const char* id,
        int type, /* 0 other 1 beacon 2 cash 3 entrance 4 exit */
        float x, float y, float w, float d,
        float rotation_deg,
        float r, float g, float b, float a);

/** Load Firebase-style store_maps JSON. Returns 1 on success. */
PLNA_API int plna_map_load_json(const char* json_utf8, char* errbuf, size_t errbuf_len);

PLNA_API int plna_map_load_file(const char* path, char* errbuf, size_t errbuf_len);

PLNA_API uint64_t plna_map_revision(void);

/**
 * Fills *out_count and returns pointer owned by the library until the next
 * mutating map call or another build. Do NOT free.
 */
PLNA_API const float* plna_map_build_draw_list(size_t* out_count);

/** Copy draw list into caller buffer; returns floats written (or needed if dst null). */
PLNA_API size_t plna_map_copy_draw_list(float* dst, size_t dst_floats);

PLNA_API void plna_map_floor_size(float* out_w, float* out_h);

PLNA_API void plna_map_stats(
        uint32_t* equipment,
        uint32_t* walls,
        uint32_t* markers,
        uint32_t* route_points,
        uint64_t* revision);

/** Route; writes up to max_floats floats (x,y pairs). Returns floats written. */
PLNA_API size_t plna_map_route(
        float from_x, float from_y, float to_x, float to_y,
        float* out_xy, size_t max_floats,
        int apply_to_scene);

PLNA_API void plna_map_clear_route(void);

PLNA_API void plna_product_put(const char* barcode, float x, float y, float z, float shelf);
PLNA_API int plna_product_find(const char* barcode, float* out_xyzw /*4*/);
PLNA_API void plna_product_clear(void);

#ifdef __cplusplus
}
#endif
