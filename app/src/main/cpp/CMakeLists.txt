cmake_minimum_required(VERSION 3.22.1)

project(plna LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

option(PLNA_BUILD_ANDROID_JNI "Build Android JNI into libplna.so" OFF)
option(PLNA_BUILD_TOOLS "Build map_dump CLI" ON)
option(PLNA_BUILD_TESTS "Build smoke tests" ON)

if(ANDROID)
    set(PLNA_BUILD_ANDROID_JNI ON CACHE BOOL "" FORCE)
endif()

set(PLNA_CORE_SOURCES
        core/src/map_geometry.cpp
        core/src/map_scene.cpp
        core/src/socket_client.cpp
        core/src/store_map_loader.cpp
        core/src/c_api.cpp
)

add_library(plna_core STATIC ${PLNA_CORE_SOURCES})
target_include_directories(plna_core PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}/core/include
        ${CMAKE_CURRENT_SOURCE_DIR}/third_party
)
target_compile_options(plna_core PRIVATE -Wall -Wextra $<$<NOT:$<CONFIG:Debug>>:-O3>)

find_package(Threads REQUIRED)
target_link_libraries(plna_core PUBLIC Threads::Threads)

if(PLNA_BUILD_ANDROID_JNI)
    add_library(plna SHARED
            platform/android/jni_map_bridge.cpp
            platform/android/jni_network_bridge.cpp
    )
    target_link_libraries(plna PUBLIC plna_core log)
    target_compile_options(plna PRIVATE -Wall -Wextra $<$<NOT:$<CONFIG:Debug>>:-O3>)
else()
    add_library(plna ALIAS plna_core)
endif()

if(PLNA_BUILD_TOOLS AND NOT ANDROID)
    add_executable(plna_map_dump tools/map_dump.cpp)
    target_link_libraries(plna_map_dump PRIVATE plna_core)
endif()

enable_testing()
if(PLNA_BUILD_TESTS AND NOT ANDROID)
    add_executable(test_store_map tests/test_store_map.cpp)
    target_link_libraries(test_store_map PRIVATE plna_core)
    add_test(
            NAME store_map_fixture
            COMMAND test_store_map ${CMAKE_SOURCE_DIR}/fixtures/store_map_sample.json
    )
endif()
