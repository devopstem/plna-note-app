#pragma once

#include <cstdint>
#include <string>
#include <vector>

namespace plna::math {

struct Vector3 {
    float x;
    float y;
    float z;
};

struct ProductHit {
    float x;
    float y;
    float z;
    float shelf;
    bool found;
};

/** Маршрут по сетке зала: ломаная от from → to (упрощённый L-path). */
std::vector<float> calculateRoute(float fromX, float fromY, float toX, float toY);

/** Вершины стеллажа для отрисовки [x,y,z, ...]. */
std::vector<float> drawRack(
        const std::string& rackId,
        float startX,
        float startY,
        float width,
        float depth,
        int shelves,
        float shelfHeight
);

/** Поиск позиции товара по штрихкоду в нативном кэше. */
ProductHit findProduct(const std::string& barcode);

void putProduct(const std::string& barcode, float x, float y, float z, float shelf);
void clearProducts();

/** Пакетный расчёт позиций на полках (legacy / visualizer). */
std::vector<Vector3> calculateBatchPositions(
        const std::vector<float>& starts,
        const std::vector<float>& ends,
        const std::vector<int32_t>& shelves,
        float shelfHeight
);

} // namespace plna::math
