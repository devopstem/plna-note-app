#pragma once

#include <cctype>
#include <cstdint>
#include <string_view>

namespace plna {

struct Rgba {
    float r = 1.f;
    float g = 1.f;
    float b = 1.f;
    float a = 1.f;
};

/** Parse #RGB, #RRGGBB, #RRGGBBAA (optional leading #). On failure keeps `fallback`. */
inline Rgba parseCssColor(std::string_view text, Rgba fallback = {0.39f, 0.40f, 0.95f, 1.f}) {
    while (!text.empty() && std::isspace(static_cast<unsigned char>(text.front()))) {
        text.remove_prefix(1);
    }
    if (!text.empty() && text.front() == '#') {
        text.remove_prefix(1);
    }
    auto hex = [](char c) -> int {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return 10 + c - 'a';
        if (c >= 'A' && c <= 'F') return 10 + c - 'A';
        return -1;
    };
    auto byteAt = [&](size_t i) -> int {
        const int hi = hex(text[i]);
        const int lo = hex(text[i + 1]);
        if (hi < 0 || lo < 0) return -1;
        return (hi << 4) | lo;
    };

    Rgba out = fallback;
    if (text.size() == 3 || text.size() == 4) {
        int r = hex(text[0]), g = hex(text[1]), b = hex(text[2]);
        if (r < 0 || g < 0 || b < 0) return fallback;
        out.r = static_cast<float>((r << 4) | r) / 255.f;
        out.g = static_cast<float>((g << 4) | g) / 255.f;
        out.b = static_cast<float>((b << 4) | b) / 255.f;
        if (text.size() == 4) {
            int a = hex(text[3]);
            if (a < 0) return fallback;
            out.a = static_cast<float>((a << 4) | a) / 255.f;
        }
        return out;
    }
    if (text.size() == 6 || text.size() == 8) {
        int r = byteAt(0), g = byteAt(2), b = byteAt(4);
        if (r < 0 || g < 0 || b < 0) return fallback;
        out.r = static_cast<float>(r) / 255.f;
        out.g = static_cast<float>(g) / 255.f;
        out.b = static_cast<float>(b) / 255.f;
        if (text.size() == 8) {
            int a = byteAt(6);
            if (a < 0) return fallback;
            out.a = static_cast<float>(a) / 255.f;
        }
        return out;
    }
    return fallback;
}

} // namespace plna
