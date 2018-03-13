#pragma once

#include <mbgl/storage/offline.hpp>
#include <jni/jni.hpp>

namespace mbgl {
namespace android {

class OfflineRegionDefinition {
public:
    static constexpr auto Name() { return "com/mapbox/mapboxsdk/offline/OfflineRegionDefinition"; };

    static jni::Object<OfflineRegionDefinition> New(jni::JNIEnv&, mbgl::OfflineRegionDefinition);

    static mbgl::OfflineRegionDefinition getDefinition(jni::JNIEnv&, jni::Object<OfflineRegionDefinition>);

    static jni::Class<OfflineRegionDefinition> javaClass;

    static void registerNative(jni::JNIEnv&);

};

} // namespace android
} // namespace mbgl
