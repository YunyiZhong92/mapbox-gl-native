#include "offline_region_definition.hpp"

#include "../geojson/geometry.hpp"

namespace mbgl {
namespace android {

// OfflineRegionDefinition //

jni::Object<OfflineRegionDefinition> OfflineRegionDefinition::New(jni::JNIEnv& env, mbgl::OfflineRegionDefinition definition) {

    // Convert objects
    auto styleURL = jni::Make<jni::String>(env, definition.styleURL);
    auto geometry = geojson::Geometry::New(env, definition.geometry);

    static auto constructor = javaClass.GetConstructor<jni::String, jni::Object<geojson::Geometry>, jni::jdouble, jni::jdouble, jni::jfloat>(env);
    auto jdefinition = javaClass.New(env, constructor, styleURL, geometry, definition.minZoom, definition.maxZoom, definition.pixelRatio);

    // Delete References
    jni::DeleteLocalRef(env, styleURL);
    jni::DeleteLocalRef(env, geometry);

    return jdefinition;
}

mbgl::OfflineRegionDefinition OfflineRegionDefinition::getDefinition(jni::JNIEnv& env, jni::Object<OfflineRegionDefinition> jDefinition) {
    // Field references
    static auto styleURLF = javaClass.GetField<jni::String>(env, "styleURL");
    static auto geometryF = javaClass.GetField<jni::Object<geojson::Geometry>>(env, "geometry");
    static auto minZoomF = javaClass.GetField<jni::jdouble>(env, "minZoom");
    static auto maxZoomF = javaClass.GetField<jni::jdouble>(env, "maxZoom");
    static auto pixelRatioF = javaClass.GetField<jni::jfloat>(env, "pixelRatio");

    // Get objects
    auto jStyleURL = jDefinition.Get(env, styleURLF);
    auto jGeometry = jDefinition.Get(env, geometryF);

    // Create definition
    mbgl::OfflineRegionDefinition definition(
        jni::Make<std::string>(env, jStyleURL),
        geojson::Geometry::convert(env, jGeometry),
        jDefinition.Get(env, minZoomF),
        jDefinition.Get(env, maxZoomF),
        jDefinition.Get(env, pixelRatioF)
    );

    // Delete references
    jni::DeleteLocalRef(env, jStyleURL);
    jni::DeleteLocalRef(env, jGeometry);

    return definition;
}

jni::Class<OfflineRegionDefinition> OfflineRegionDefinition::javaClass;

void OfflineRegionDefinition::registerNative(jni::JNIEnv& env) {
    javaClass = *jni::Class<OfflineRegionDefinition>::Find(env).NewGlobalRef(env).release();
}

} // namespace android
} // namespace mbgl
