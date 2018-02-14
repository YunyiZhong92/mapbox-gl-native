package com.mapbox.mapboxsdk.maps;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.Bundle;
import android.support.annotation.FloatRange;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.text.TextUtils;
import android.view.View;

import com.mapbox.geojson.Feature;
import com.mapbox.geojson.Geometry;
import com.mapbox.mapboxsdk.annotations.Annotation;
import com.mapbox.mapboxsdk.annotations.BaseMarkerOptions;
import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.annotations.Polygon;
import com.mapbox.mapboxsdk.annotations.PolygonOptions;
import com.mapbox.mapboxsdk.annotations.Polyline;
import com.mapbox.mapboxsdk.annotations.PolylineOptions;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.camera.CameraUpdate;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.constants.MapboxConstants;
import com.mapbox.mapboxsdk.constants.Style;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.geometry.LatLngBounds;
import com.mapbox.mapboxsdk.style.layers.Filter;
import com.mapbox.mapboxsdk.style.layers.Layer;
import com.mapbox.mapboxsdk.style.light.Light;
import com.mapbox.mapboxsdk.style.sources.Source;

import java.util.HashMap;
import java.util.List;

import timber.log.Timber;

/**
 * The general class to interact with in the Android Mapbox SDK. It exposes the entry point for all
 * methods related to the MapView. You cannot instantiate {@link MapboxMap} object directly, rather,
 * you must obtain one from the getMapAsync() method on a MapFragment or MapView that you have
 * added to your application.
 * <p>
 * Note: Similar to a View object, a MapboxMap should only be read and modified from the main thread.
 * </p>
 */
@UiThread
public final class MapboxMap {

  private final NativeMapView nativeMapView;

  private final UiSettings uiSettings;
  private final Projection projection;
  private final Transform transform;
  private final AnnotationManager annotationManager;
  private final CameraChangeDispatcher cameraChangeDispatcher;

  private final OnRegisterTouchListener onRegisterTouchListener;

  private MapboxMap.OnFpsChangedListener onFpsChangedListener;
  private PointF focalPoint;

  MapboxMap(NativeMapView map, Transform transform, UiSettings ui, Projection proj, OnRegisterTouchListener listener,
            AnnotationManager annotations, CameraChangeDispatcher cameraChangeDispatcher) {
    this.nativeMapView = map;
    this.uiSettings = ui;
    this.projection = proj;
    this.annotationManager = annotations.bind(this);
    this.transform = transform;
    this.onRegisterTouchListener = listener;
    this.cameraChangeDispatcher = cameraChangeDispatcher;
  }

  void initialise(@NonNull Context context, @NonNull MapboxMapOptions options) {
    transform.initialise(this, options);
    uiSettings.initialise(context, options);

    // Map configuration
    setDebugActive(options.getDebugActive());
    setApiBaseUrl(options);
    setStyleUrl(options);
    setPrefetchesTiles(options);
  }

  /**
   * Called when the hosting Activity/Fragment onStart() method is called.
   */
  void onStart() {
    nativeMapView.update();
    if (TextUtils.isEmpty(nativeMapView.getStyleUrl())) {
      // if user hasn't loaded a Style yet
      nativeMapView.setStyleUrl(Style.MAPBOX_STREETS);
    }
  }

  /**
   * Called when the hosting Activity/Fragment onStop() method is called.
   */
  void onStop() {
  }

  /**
   * Called when the hosting Activity/Fragment is going to be destroyed and map state needs to be saved.
   *
   * @param outState the bundle to save the state to.
   */
  void onSaveInstanceState(Bundle outState) {
    outState.putParcelable(MapboxConstants.STATE_CAMERA_POSITION, transform.getCameraPosition());
    outState.putBoolean(MapboxConstants.STATE_DEBUG_ACTIVE, nativeMapView.getDebug());
    outState.putString(MapboxConstants.STATE_STYLE_URL, nativeMapView.getStyleUrl());
    uiSettings.onSaveInstanceState(outState);
  }

  /**
   * Called when the hosting Activity/Fragment is recreated and map state needs to be restored.
   *
   * @param savedInstanceState the bundle containing the saved state
   */
  void onRestoreInstanceState(Bundle savedInstanceState) {
    final CameraPosition cameraPosition = savedInstanceState.getParcelable(MapboxConstants.STATE_CAMERA_POSITION);
    uiSettings.onRestoreInstanceState(savedInstanceState);
    if (cameraPosition != null) {
      moveCamera(CameraUpdateFactory.newCameraPosition(new CameraPosition.Builder(cameraPosition).build()));
    }
    nativeMapView.setDebug(savedInstanceState.getBoolean(MapboxConstants.STATE_DEBUG_ACTIVE));
    final String styleUrl = savedInstanceState.getString(MapboxConstants.STATE_STYLE_URL);
    if (!TextUtils.isEmpty(styleUrl)) {
      nativeMapView.setStyleUrl(savedInstanceState.getString(MapboxConstants.STATE_STYLE_URL));
    }
  }

  /**
   * Called before the OnMapReadyCallback is invoked.
   */
  void onPreMapReady() {
    annotationManager.reloadMarkers();
    annotationManager.adjustTopOffsetPixels(this);
  }

  /**
   * Called when the region is changing or has changed.
   */
  void onUpdateRegionChange() {
    annotationManager.update();
  }

  /**
   * Called when the map frame is fully rendered.
   */
  void onUpdateFullyRendered() {
    CameraPosition cameraPosition = transform.invalidateCameraPosition();
    if (cameraPosition != null) {
      uiSettings.update(cameraPosition);
    }
  }

  // Style

  /**
   * <p>
   * Get the animation duration for style changes.
   * </p>
   * The default value is zero, so any changes take effect without animation.
   *
   * @return Duration in milliseconds
   */
  public long getTransitionDuration() {
    return nativeMapView.getTransitionDuration();
  }

  /**
   * Set the animation duration for style changes.
   *
   * @param durationMs Duration in milliseconds
   */
  public void setTransitionDuration(long durationMs) {
    nativeMapView.setTransitionDuration(durationMs);
  }

  /**
   * <p>
   * Get the animation delay for style changes.
   * </p>
   * The default value is zero, so any changes begin to animate immediately.
   *
   * @return Delay in milliseconds
   */
  public long getTransitionDelay() {
    return nativeMapView.getTransitionDelay();
  }

  /**
   * Set the animation delay for style changes.
   *
   * @param delayMs Delay in milliseconds
   */
  public void setTransitionDelay(long delayMs) {
    nativeMapView.setTransitionDelay(delayMs);
  }

  /**
   * Sets tile pre-fetching from MapboxOptions.
   *
   * @param options the options object
   */
  private void setPrefetchesTiles(@NonNull MapboxMapOptions options) {
    setPrefetchesTiles(options.getPrefetchesTiles());
  }

  /**
   * Enable or disable tile pre-fetching. Pre-fetching makes sure that a low-resolution
   * tile is rendered as soon as possible at the expense of a little bandwidth.
   *
   * @param enable true to enable
   */
  public void setPrefetchesTiles(boolean enable) {
    nativeMapView.setPrefetchesTiles(enable);
  }

  /**
   * Check whether tile pre-fetching is enabled or not.
   *
   * @return true if enabled
   * @see MapboxMap#setPrefetchesTiles(boolean)
   */
  public boolean getPrefetchesTiles() {
    return nativeMapView.getPrefetchesTiles();
  }

  /**
   * Retrieve all the layers in the style
   *
   * @return all the layers in the current style
   */
  public List<Layer> getLayers() {
    return nativeMapView.getLayers();
  }

  /**
   * Get the layer by id
   *
   * @param layerId the layer's id
   * @return the layer, if present in the style
   */
  @Nullable
  public Layer getLayer(@NonNull String layerId) {
    return nativeMapView.getLayer(layerId);
  }

  /**
   * Tries to cast the Layer to T, returns null if it's another type.
   *
   * @param layerId the layer id used to look up a layer
   * @param <T>     the generic attribute of a Layer
   * @return the casted Layer, null if another type
   */
  @Nullable
  public <T extends Layer> T getLayerAs(@NonNull String layerId) {
    try {
      // noinspection unchecked
      return (T) nativeMapView.getLayer(layerId);
    } catch (ClassCastException exception) {
      Timber.e(exception, "Layer: %s is a different type: ", layerId);
      return null;
    }
  }

  /**
   * Adds the layer to the map. The layer must be newly created and not added to the map before
   *
   * @param layer the layer to add
   */
  public void addLayer(@NonNull Layer layer) {
    nativeMapView.addLayer(layer);
  }

  /**
   * Adds the layer to the map. The layer must be newly created and not added to the map before
   *
   * @param layer the layer to add
   * @param below the layer id to add this layer before
   */
  public void addLayerBelow(@NonNull Layer layer, @NonNull String below) {
    nativeMapView.addLayerBelow(layer, below);
  }

  /**
   * Adds the layer to the map. The layer must be newly created and not added to the map before
   *
   * @param layer the layer to add
   * @param above the layer id to add this layer above
   */
  public void addLayerAbove(@NonNull Layer layer, @NonNull String above) {
    nativeMapView.addLayerAbove(layer, above);
  }

  /**
   * Adds the layer to the map at the specified index. The layer must be newly
   * created and not added to the map before
   *
   * @param layer the layer to add
   * @param index the index to insert the layer at
   */
  public void addLayerAt(@NonNull Layer layer, @IntRange(from = 0) int index) {
    nativeMapView.addLayerAt(layer, index);
  }

  /**
   * Removes the layer. Any references to the layer become invalid and should not be used anymore
   *
   * @param layerId the layer to remove
   * @return the removed layer or null if not found
   */
  @Nullable
  public Layer removeLayer(@NonNull String layerId) {
    return nativeMapView.removeLayer(layerId);
  }

  /**
   * Removes the layer. The reference is re-usable after this and can be re-added
   *
   * @param layer the layer to remove
   * @return the layer
   */
  @Nullable
  public Layer removeLayer(@NonNull Layer layer) {
    return nativeMapView.removeLayer(layer);
  }

  /**
   * Removes the layer. Any other references to the layer become invalid and should not be used anymore
   *
   * @param index the layer index
   * @return the removed layer or null if not found
   */
  @Nullable
  public Layer removeLayerAt(@IntRange(from = 0) int index) {
    return nativeMapView.removeLayerAt(index);
  }

  /**
   * Retrieve all the sources in the style
   *
   * @return all the sources in the current style
   */
  public List<Source> getSources() {
    return nativeMapView.getSources();
  }

  /**
   * Retrieve a source by id
   *
   * @param sourceId the source's id
   * @return the source if present in the current style
   */
  @Nullable
  public Source getSource(@NonNull String sourceId) {
    return nativeMapView.getSource(sourceId);
  }

  /**
   * Tries to cast the Source to T, returns null if it's another type.
   *
   * @param sourceId the id used to look up a layer
   * @param <T>      the generic type of a Source
   * @return the casted Source, null if another type
   */
  @Nullable
  public <T extends Source> T getSourceAs(@NonNull String sourceId) {
    try {
      // noinspection unchecked
      return (T) nativeMapView.getSource(sourceId);
    } catch (ClassCastException exception) {
      Timber.e(exception, "Source: %s is a different type: ", sourceId);
      return null;
    }
  }

  /**
   * Adds the source to the map. The source must be newly created and not added to the map before
   *
   * @param source the source to add
   */
  public void addSource(@NonNull Source source) {
    nativeMapView.addSource(source);
  }

  /**
   * Removes the source. Any references to the source become invalid and should not be used anymore
   *
   * @param sourceId the source to remove
   * @return the source handle or null if the source was not present
   */
  @Nullable
  public Source removeSource(@NonNull String sourceId) {
    return nativeMapView.removeSource(sourceId);
  }

  /**
   * Removes the source, preserving the reverence for re-use
   *
   * @param source the source to remove
   * @return the source
   */
  @Nullable
  public Source removeSource(@NonNull Source source) {
    return nativeMapView.removeSource(source);
  }

  /**
   * Adds an image to be used in the map's style
   *
   * @param name  the name of the image
   * @param image the pre-multiplied Bitmap
   */
  public void addImage(@NonNull String name, @NonNull Bitmap image) {
    nativeMapView.addImage(name, image);
  }

  /**
   * Adds an images to be used in the map's style
   */
  public void addImages(@NonNull HashMap<String, Bitmap> images) {
    nativeMapView.addImages(images);
  }

  /**
   * Removes an image from the map's style
   *
   * @param name the name of the image to remove
   */
  public void removeImage(String name) {
    nativeMapView.removeImage(name);
  }

  public Bitmap getImage(@NonNull String name) {
    return nativeMapView.getImage(name);
  }

  //
  // MinZoom
  //

  /**
   * <p>
   * Sets the minimum zoom level the map can be displayed at.
   * </p>
   *
   * @param minZoom The new minimum zoom level.
   */
  public void setMinZoomPreference(
    @FloatRange(from = MapboxConstants.MINIMUM_ZOOM, to = MapboxConstants.MAXIMUM_ZOOM) double minZoom) {
    transform.setMinZoom(minZoom);
  }

  /**
   * <p>
   * Gets the minimum zoom level the map can be displayed at.
   * </p>
   *
   * @return The minimum zoom level.
   */
  public double getMinZoomLevel() {
    return transform.getMinZoom();
  }

  //
  // MaxZoom
  //

  /**
   * <p>
   * Sets the maximum zoom level the map can be displayed at.
   * </p>
   * <p>
   * The default maximum zoomn level is 22. The upper bound for this value is 25.5.
   * </p>
   *
   * @param maxZoom The new maximum zoom level.
   */
  public void setMaxZoomPreference(@FloatRange(from = MapboxConstants.MINIMUM_ZOOM,
    to = MapboxConstants.MAXIMUM_ZOOM) double maxZoom) {
    transform.setMaxZoom(maxZoom);
  }

  /**
   * <p>
   * Gets the maximum zoom level the map can be displayed at.
   * </p>
   *
   * @return The maximum zoom level.
   */
  public double getMaxZoomLevel() {
    return transform.getMaxZoom();
  }

  //
  // UiSettings
  //

  /**
   * Gets the user interface settings for the map.
   *
   * @return the UiSettings associated with this map
   */
  public UiSettings getUiSettings() {
    return uiSettings;
  }

  //
  // Projection
  //

  /**
   * Get the Projection object that you can use to convert between screen coordinates and latitude/longitude
   * coordinates.
   *
   * @return the Projection associated with this map
   */
  public Projection getProjection() {
    return projection;
  }

  //
  //
  //

  /**
   * Get the global light source used to change lighting conditions on extruded fill layers.
   *
   * @return the global light source
   */
  @Nullable
  public Light getLight() {
    return nativeMapView.getLight();
  }

  //
  // Camera API
  //

  /**
   * Moves the center of the screen to a latitude and longitude specified by a LatLng object. This centers the
   * camera on the LatLng object.
   * <p>
   * Note that at low zoom levels, setLatLng is constrained so that the entire viewport shows map data.
   * </p>
   *
   * @param latLng Target location to change to
   */
  public void setLatLng(@NonNull LatLng latLng) {
    nativeMapView.setLatLng(latLng);
  }

  /**
   * Moves the camera viewpoint to a particular zoom level.
   *
   * @param zoom Zoom level to change to
   */
  public void setZoom(@FloatRange(from = MapboxConstants.MINIMUM_ZOOM, to = MapboxConstants.MAXIMUM_ZOOM) double zoom) {
    if (focalPoint == null) {
      focalPoint = new PointF(nativeMapView.getWidth() / 2, nativeMapView.getHeight() / 2);
    }
    nativeMapView.setZoom(zoom, focalPoint, 0);
  }

  /**
   * Moves the center and the zoom of the camera specified by a LatLng object and double zoom.
   * <p>
   * Note that at low zoom levels, setLatLng is constrained so that the entire viewport shows map data.
   * </p>
   *
   * @param latLng Target location to change to
   */
  public void setLatLngZoom(@NonNull LatLng latLng,
                            @FloatRange(from = MapboxConstants.MINIMUM_ZOOM,
                              to = MapboxConstants.MAXIMUM_ZOOM) double zoom) {
    setZoom(zoom);
    setLatLng(latLng);
  }

  /**
   * Moves the camera viewpoint angle to a particular angle in degrees.
   *
   * @param tilt Tilt angle to change to
   */
  public void setTilt(@FloatRange(from = MapboxConstants.MINIMUM_TILT, to = MapboxConstants.MAXIMUM_TILT) double tilt) {
    nativeMapView.setPitch(tilt, 0);
  }

  /**
   * Moves the camera viewpoint direction to a particular angle in degrees.
   *
   * @param bearing Direction angle to change to
   */
  public void setBearing(@FloatRange(from = MapboxConstants.MINIMUM_DIRECTION, to = MapboxConstants.MAXIMUM_DIRECTION)
                           double bearing) {
    nativeMapView.setBearing(bearing);
  }

  /**
   * Cancels ongoing animations.
   * <p>
   * This invokes the {@link CancelableCallback} for ongoing camera updates.
   * </p>
   */
  public void cancelTransitions() {
    transform.cancelTransitions();
  }

  /**
   * Gets the current position of the camera.
   * The CameraPosition returned is a snapshot of the current position, and will not automatically update when the
   * camera moves.
   *
   * @return The current position of the Camera.
   */
  public final CameraPosition getCameraPosition() {
    return transform.getCameraPosition();
  }

  /**
   * Repositions the camera according to the cameraPosition.
   * The move is instantaneous, and a subsequent getCameraPosition() will reflect the new position.
   * See CameraUpdateFactory for a set of updates.
   *
   * @param cameraPosition the camera position to set
   */
  public void setCameraPosition(@NonNull CameraPosition cameraPosition) {
    moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), null);
  }

  /**
   * Repositions the camera according to the instructions defined in the update.
   * The move is instantaneous, and a subsequent getCameraPosition() will reflect the new position.
   * See CameraUpdateFactory for a set of updates.
   *
   * @param update The change that should be applied to the camera.
   */
  public final void moveCamera(CameraUpdate update) {
    moveCamera(update, null);
  }

  /**
   * Repositions the camera according to the instructions defined in the update.
   * The move is instantaneous, and a subsequent getCameraPosition() will reflect the new position.
   * See CameraUpdateFactory for a set of updates.
   *
   * @param update   The change that should be applied to the camera
   * @param callback the callback to be invoked when an animation finishes or is canceled
   */
  public final void moveCamera(final CameraUpdate update, final MapboxMap.CancelableCallback callback) {
    transform.moveCamera(MapboxMap.this, update, callback);
  }

  /**
   * Gradually move the camera by the default duration, zoom will not be affected unless specified
   * within {@link CameraUpdate}. If {@link #getCameraPosition()} is called during the animation,
   * it will return the current location of the camera in flight.
   *
   * @param update The change that should be applied to the camera.
   * @see com.mapbox.mapboxsdk.camera.CameraUpdateFactory for a set of updates.
   */
  public final void easeCamera(CameraUpdate update) {
    easeCamera(update, MapboxConstants.ANIMATION_DURATION);
  }

  /**
   * Gradually move the camera by a specified duration in milliseconds, zoom will not be affected
   * unless specified within {@link CameraUpdate}. If {@link #getCameraPosition()} is called
   * during the animation, it will return the current location of the camera in flight.
   *
   * @param update     The change that should be applied to the camera.
   * @param durationMs The duration of the animation in milliseconds. This must be strictly
   *                   positive, otherwise an IllegalArgumentException will be thrown.
   * @see com.mapbox.mapboxsdk.camera.CameraUpdateFactory for a set of updates.
   */
  public final void easeCamera(CameraUpdate update, int durationMs) {
    easeCamera(update, durationMs, null);
  }

  /**
   * Gradually move the camera by a specified duration in milliseconds, zoom will not be affected
   * unless specified within {@link CameraUpdate}. A callback can be used to be notified when
   * easing the camera stops. If {@link #getCameraPosition()} is called during the animation, it
   * will return the current location of the camera in flight.
   * <p>
   * Note that this will cancel location tracking mode if enabled.
   * </p>
   *
   * @param update     The change that should be applied to the camera.
   * @param durationMs The duration of the animation in milliseconds. This must be strictly
   *                   positive, otherwise an IllegalArgumentException will be thrown.
   * @param callback   An optional callback to be notified from the main thread when the animation
   *                   stops. If the animation stops due to its natural completion, the callback
   *                   will be notified with onFinish(). If the animation stops due to interruption
   *                   by a later camera movement or a user gesture, onCancel() will be called.
   *                   Do not update or ease the camera from within onCancel().
   * @see com.mapbox.mapboxsdk.camera.CameraUpdateFactory for a set of updates.
   */
  public final void easeCamera(CameraUpdate update, int durationMs, final MapboxMap.CancelableCallback callback) {
    easeCamera(update, durationMs, true, callback);
  }

  /**
   * Gradually move the camera by a specified duration in milliseconds, zoom will not be affected
   * unless specified within {@link CameraUpdate}. A callback can be used to be notified when
   * easing the camera stops. If {@link #getCameraPosition()} is called during the animation, it
   * will return the current location of the camera in flight.
   * <p>
   * Note that this will cancel location tracking mode if enabled.
   * </p>
   *
   * @param update             The change that should be applied to the camera.
   * @param durationMs         The duration of the animation in milliseconds. This must be strictly
   *                           positive, otherwise an IllegalArgumentException will be thrown.
   * @param easingInterpolator True for easing interpolator, false for linear.
   */
  public final void easeCamera(CameraUpdate update, int durationMs, boolean easingInterpolator) {
    easeCamera(update, durationMs, easingInterpolator, null);
  }

  /**
   * Gradually move the camera by a specified duration in milliseconds, zoom will not be affected
   * unless specified within {@link CameraUpdate}. A callback can be used to be notified when
   * easing the camera stops. If {@link #getCameraPosition()} is called during the animation, it
   * will return the current location of the camera in flight.
   *
   * @param update             The change that should be applied to the camera.
   * @param durationMs         The duration of the animation in milliseconds. This must be strictly
   *                           positive, otherwise an IllegalArgumentException will be thrown.
   * @param easingInterpolator True for easing interpolator, false for linear.
   * @param callback           An optional callback to be notified from the main thread when the animation
   *                           stops. If the animation stops due to its natural completion, the callback
   *                           will be notified with onFinish(). If the animation stops due to interruption
   *                           by a later camera movement or a user gesture, onCancel() will be called.
   *                           Do not update or ease the camera from within onCancel().
   */
  public final void easeCamera(final CameraUpdate update, final int durationMs, final boolean easingInterpolator,
                               final MapboxMap.CancelableCallback callback) {
    easeCamera(update, durationMs, easingInterpolator, callback, false);
  }

  /**
   * Gradually move the camera by a specified duration in milliseconds, zoom will not be affected
   * unless specified within {@link CameraUpdate}. A callback can be used to be notified when
   * easing the camera stops. If {@link #getCameraPosition()} is called during the animation, it
   * will return the current location of the camera in flight.
   *
   * @param update             The change that should be applied to the camera.
   * @param durationMs         The duration of the animation in milliseconds. This must be strictly
   *                           positive, otherwise an IllegalArgumentException will be thrown.
   * @param easingInterpolator True for easing interpolator, false for linear.
   * @param callback           An optional callback to be notified from the main thread when the animation
   *                           stops. If the animation stops due to its natural completion, the callback
   *                           will be notified with onFinish(). If the animation stops due to interruption
   *                           by a later camera movement or a user gesture, onCancel() will be called.
   *                           Do not update or ease the camera from within onCancel().
   * @param isDismissable      true will allow animated camera changes dismiss a tracking mode.
   */
  public final void easeCamera(final CameraUpdate update, final int durationMs, final boolean easingInterpolator,
                               final MapboxMap.CancelableCallback callback, final boolean isDismissable) {

    if (durationMs <= 0) {
      throw new IllegalArgumentException("Null duration passed into easeCamera");
    }
    transform.easeCamera(MapboxMap.this, update, durationMs, easingInterpolator, callback, isDismissable);
  }

  /**
   * Animate the camera to a new location defined within {@link CameraUpdate} using a transition
   * animation that evokes powered flight. The animation will last the default amount of time.
   * During the animation, a call to {@link #getCameraPosition()} returns an intermediate location
   * of the camera in flight.
   *
   * @param update The change that should be applied to the camera.
   * @see com.mapbox.mapboxsdk.camera.CameraUpdateFactory for a set of updates.
   */
  public final void animateCamera(CameraUpdate update) {
    animateCamera(update, MapboxConstants.ANIMATION_DURATION, null);
  }

  /**
   * Animate the camera to a new location defined within {@link CameraUpdate} using a transition
   * animation that evokes powered flight. The animation will last the default amount of time. A
   * callback can be used to be notified when animating the camera stops. During the animation, a
   * call to {@link #getCameraPosition()} returns an intermediate location of the camera in flight.
   *
   * @param update   The change that should be applied to the camera.
   * @param callback The callback to invoke from the main thread when the animation stops. If the
   *                 animation completes normally, onFinish() is called; otherwise, onCancel() is
   *                 called. Do not update or animate the camera from within onCancel().
   * @see com.mapbox.mapboxsdk.camera.CameraUpdateFactory for a set of updates.
   */
  public final void animateCamera(CameraUpdate update, MapboxMap.CancelableCallback callback) {
    animateCamera(update, MapboxConstants.ANIMATION_DURATION, callback);
  }

  /**
   * Animate the camera to a new location defined within {@link CameraUpdate} using a transition
   * animation that evokes powered flight. The animation will last a specified amount of time
   * given in milliseconds. During the animation, a call to {@link #getCameraPosition()} returns
   * an intermediate location of the camera in flight.
   *
   * @param update     The change that should be applied to the camera.
   * @param durationMs The duration of the animation in milliseconds. This must be strictly
   *                   positive, otherwise an IllegalArgumentException will be thrown.
   * @see com.mapbox.mapboxsdk.camera.CameraUpdateFactory for a set of updates.
   */
  public final void animateCamera(CameraUpdate update, int durationMs) {
    animateCamera(update, durationMs, null);
  }

  /**
   * Animate the camera to a new location defined within {@link CameraUpdate} using a transition
   * animation that evokes powered flight. The animation will last a specified amount of time
   * given in milliseconds. A callback can be used to be notified when animating the camera stops.
   * During the animation, a call to {@link #getCameraPosition()} returns an intermediate location
   * of the camera in flight.
   *
   * @param update     The change that should be applied to the camera.
   * @param durationMs The duration of the animation in milliseconds. This must be strictly
   *                   positive, otherwise an IllegalArgumentException will be thrown.
   * @param callback   An optional callback to be notified from the main thread when the animation
   *                   stops. If the animation stops due to its natural completion, the callback
   *                   will be notified with onFinish(). If the animation stops due to interruption
   *                   by a later camera movement or a user gesture, onCancel() will be called.
   *                   Do not update or animate the camera from within onCancel(). If a callback
   *                   isn't required, leave it as null.
   * @see com.mapbox.mapboxsdk.camera.CameraUpdateFactory for a set of updates.
   */
  public final void animateCamera(final CameraUpdate update, final int durationMs,
                                  final MapboxMap.CancelableCallback callback) {
    if (durationMs <= 0) {
      throw new IllegalArgumentException("Null duration passed into animageCamera");
    }

    transform.animateCamera(MapboxMap.this, update, durationMs, callback);
  }

  //
  //  Reset North
  //

  /**
   * Resets the map view to face north.
   */
  public void resetNorth() {
    transform.resetNorth();
  }

  /**
   * Transform the map bearing given a bearing, focal point coordinates, and a duration.
   *
   * @param bearing  The bearing of the Map to be transformed to
   * @param focalX   The x coordinate of the focal point
   * @param focalY   The y coordinate of the focal point
   * @param duration The duration of the transformation
   */
  public void setFocalBearing(double bearing, float focalX, float focalY, long duration) {
    transform.setBearing(bearing, focalX, focalY, duration);
  }

  /**
   * Returns the measured height of the Map.
   *
   * @return the height of the map
   */
  public float getHeight() {
    return nativeMapView.getHeight();
  }

  /**
   * Returns the measured width of the Map.
   *
   * @return the width of the map
   */
  public float getWidth() {
    return nativeMapView.getWidth();
  }

  //
  // Debug
  //

  /**
   * Returns whether the map debug information is currently shown.
   *
   * @return If true, map debug information is currently shown.
   */
  public boolean isDebugActive() {
    return nativeMapView.getDebug();
  }

  /**
   * <p>
   * Changes whether the map debug information is shown.
   * </p>
   * The default value is false.
   *
   * @param debugActive If true, map debug information is shown.
   */
  public void setDebugActive(boolean debugActive) {
    nativeMapView.setDebug(debugActive);
  }

  /**
   * <p>
   * Cycles through the map debug options.
   * </p>
   * The value of isDebugActive reflects whether there are
   * any map debug options enabled or disabled.
   *
   * @see #isDebugActive()
   */
  public void cycleDebugOptions() {
    nativeMapView.cycleDebugOptions();
  }

  //
  // API endpoint config
  //

  private void setApiBaseUrl(@NonNull MapboxMapOptions options) {
    String apiBaseUrl = options.getApiBaseUrl();
    if (!TextUtils.isEmpty(apiBaseUrl)) {
      nativeMapView.setApiBaseUrl(apiBaseUrl);
    }
  }

  //
  // Styling
  //

  /**
   * <p>
   * Loads a new map style asynchronous from the specified URL.
   * </p>
   * {@code url} can take the following forms:
   * <ul>
   * <li>{@code Style.*}: load one of the bundled styles in {@link Style}.</li>
   * <li>{@code mapbox://styles/<user>/<style>}:
   * loads the style from a <a href="https://www.mapbox.com/account/">Mapbox account.</a>
   * {@code user} is your username. {@code style} is the ID of your custom
   * style created in <a href="https://www.mapbox.com/studio">Mapbox Studio</a>.</li>
   * <li>{@code http://...} or {@code https://...}:
   * loads the style over the Internet from any web server.</li>
   * <li>{@code asset://...}:
   * loads the style from the APK {@code assets/} directory.
   * This is used to load a style bundled with your app.</li>
   * <li>{@code null}: loads the default {@link Style#MAPBOX_STREETS} style.</li>
   * </ul>
   * <p>
   * This method is asynchronous and will return before the style finishes loading.
   * If you wish to wait for the map to finish loading, listen for the {@link MapView#DID_FINISH_LOADING_MAP} event
   * or use the {@link #setStyleUrl(String, OnStyleLoadedListener)} method instead.
   * </p>
   * If the style fails to load or an invalid style URL is set, the map view will become blank.
   * An error message will be logged in the Android logcat and {@link MapView#DID_FAIL_LOADING_MAP} event will be
   * emitted.
   *
   * @param url The URL of the map style
   * @see Style
   */
  public void setStyleUrl(@NonNull String url) {
    setStyleUrl(url, null);
  }

  /**
   * <p>
   * Loads a new map style asynchronous from the specified URL.
   * </p>
   * {@code url} can take the following forms:
   * <ul>
   * <li>{@code Style.*}: load one of the bundled styles in {@link Style}.</li>
   * <li>{@code mapbox://styles/<user>/<style>}:
   * loads the style from a <a href="https://www.mapbox.com/account/">Mapbox account.</a>
   * {@code user} is your username. {@code style} is the ID of your custom
   * style created in <a href="https://www.mapbox.com/studio">Mapbox Studio</a>.</li>
   * <li>{@code http://...} or {@code https://...}:
   * loads the style over the Internet from any web server.</li>
   * <li>{@code asset://...}:
   * loads the style from the APK {@code assets/} directory.
   * This is used to load a style bundled with your app.</li>
   * <li>{@code null}: loads the default {@link Style#MAPBOX_STREETS} style.</li>
   * </ul>
   * <p>
   * If the style fails to load or an invalid style URL is set, the map view will become blank.
   * An error message will be logged in the Android logcat and {@link MapView#DID_FAIL_LOADING_MAP} event will be
   * emitted.
   * <p>
   *
   * @param url      The URL of the map style
   * @param callback The callback that is invoked when the style has loaded.
   * @see Style
   */
  public void setStyleUrl(@NonNull final String url, @Nullable final OnStyleLoadedListener callback) {
    if (callback != null) {
      nativeMapView.addOnMapChangedListener(new MapView.OnMapChangedListener() {
        @Override
        public void onMapChanged(@MapView.MapChange int change) {
          if (change == MapView.DID_FINISH_LOADING_STYLE) {
            callback.onStyleLoaded(url);
            nativeMapView.removeOnMapChangedListener(this);
          }
        }
      });
    }
    nativeMapView.setStyleUrl(url);
  }

  /**
   * <p>
   * Loads a new map style from the specified bundled style.
   * </p>
   * <p>
   * This method is asynchronous and will return before the style finishes loading.
   * If you wish to wait for the map to finish loading, listen for the {@link MapView#DID_FINISH_LOADING_MAP} event
   * or use the {@link #setStyle(String, OnStyleLoadedListener)} method instead.
   * </p>
   * If the style fails to load or an invalid style URL is set, the map view will become blank.
   * An error message will be logged in the Android logcat and {@link MapView#DID_FAIL_LOADING_MAP} event will be
   * sent.
   *
   * @param style The bundled style.
   * @see Style
   */
  public void setStyle(@Style.StyleUrl String style) {
    setStyleUrl(style);
  }

  /**
   * <p>
   * Loads a new map style from the specified bundled style.
   * </p>
   * If the style fails to load or an invalid style URL is set, the map view will become blank.
   * An error message will be logged in the Android logcat and {@link MapView#DID_FAIL_LOADING_MAP} event will be
   * sent.
   *
   * @param style    The bundled style.
   * @param callback The callback to be invoked when the style has finished loading
   * @see Style
   */
  public void setStyle(@Style.StyleUrl String style, @Nullable OnStyleLoadedListener callback) {
    setStyleUrl(style, callback);
  }

  /**
   * Loads a new map style from MapboxMapOptions if available.
   *
   * @param options the object containing the style url
   */
  private void setStyleUrl(@NonNull MapboxMapOptions options) {
    String style = options.getStyle();
    if (!TextUtils.isEmpty(style)) {
      setStyleUrl(style, null);
    }
  }

  /**
   * Returns the map style url currently displayed in the map view.
   *
   * @return The URL of the map style
   */
  @Nullable
  public String getStyleUrl() {
    return nativeMapView.getStyleUrl();
  }

  /**
   * Loads a new map style from a json string.
   * <p>
   * If the style fails to load or an invalid style URL is set, the map view will become blank.
   * An error message will be logged in the Android logcat and {@link MapView#DID_FAIL_LOADING_MAP} event will be
   * sent.
   * </p>
   */
  public void setStyleJson(@NonNull String styleJson) {
    nativeMapView.setStyleJson(styleJson);
  }

  /**
   * Returns the map style json currently displayed in the map view.
   *
   * @return The json of the map style
   */
  public String getStyleJson() {
    return nativeMapView.getStyleJson();
  }

  //
  // Annotations
  //

  /**
   * <p>
   * Adds a marker to this map.
   * </p>
   * The marker's icon is rendered on the map at the location {@code Marker.position}.
   * If {@code Marker.title} is defined, the map shows an info box with the marker's title and snippet.
   *
   * @param markerOptions A marker options object that defines how to render the marker
   * @return The {@code Marker} that was added to the map
   */
  @NonNull
  public Marker addMarker(@NonNull MarkerOptions markerOptions) {
    return annotationManager.addMarker(markerOptions, this);
  }

  /**
   * <p>
   * Adds a marker to this map.
   * </p>
   * The marker's icon is rendered on the map at the location {@code Marker.position}.
   * If {@code Marker.title} is defined, the map shows an info box with the marker's title and snippet.
   *
   * @param markerOptions A marker options object that defines how to render the marker
   * @return The {@code Marker} that was added to the map
   */
  @NonNull
  public Marker addMarker(@NonNull BaseMarkerOptions markerOptions) {
    return annotationManager.addMarker(markerOptions, this);
  }

  /**
   * <p>
   * Adds multiple markers to this map.
   * </p>
   * The marker's icon is rendered on the map at the location {@code Marker.position}.
   * If {@code Marker.title} is defined, the map shows an info box with the marker's title and snippet.
   *
   * @param markerOptionsList A list of marker options objects that defines how to render the markers
   * @return A list of the {@code Marker}s that were added to the map
   */
  @NonNull
  public List<Marker> addMarkers(@NonNull List<? extends
    BaseMarkerOptions> markerOptionsList) {
    return annotationManager.addMarkers(markerOptionsList, this);
  }

  /**
   * <p>
   * Updates a marker on this map. Does nothing if the marker isn't already added.
   * </p>
   *
   * @param updatedMarker An updated marker object
   */
  public void updateMarker(@NonNull Marker updatedMarker) {
    annotationManager.updateMarker(updatedMarker, this);
  }

  /**
   * Adds a polyline to this map.
   *
   * @param polylineOptions A polyline options object that defines how to render the polyline
   * @return The {@code Polyine} that was added to the map
   */
  @NonNull
  public Polyline addPolyline(@NonNull PolylineOptions polylineOptions) {
    return annotationManager.addPolyline(polylineOptions, this);
  }

  /**
   * Adds multiple polylines to this map.
   *
   * @param polylineOptionsList A list of polyline options objects that defines how to render the polylines.
   * @return A list of the {@code Polyline}s that were added to the map.
   */
  @NonNull
  public List<Polyline> addPolylines(@NonNull List<PolylineOptions> polylineOptionsList) {
    return annotationManager.addPolylines(polylineOptionsList, this);
  }

  /**
   * Update a polyline on this map.
   *
   * @param polyline An updated polyline object.
   */
  public void updatePolyline(Polyline polyline) {
    annotationManager.updatePolyline(polyline);
  }

  /**
   * Adds a polygon to this map.
   *
   * @param polygonOptions A polygon options object that defines how to render the polygon.
   * @return The {@code Polygon} that was added to the map.
   */
  @NonNull
  public Polygon addPolygon(@NonNull PolygonOptions polygonOptions) {
    return annotationManager.addPolygon(polygonOptions, this);
  }

  /**
   * Adds multiple polygons to this map.
   *
   * @param polygonOptionsList A list of polygon options objects that defines how to render the polygons
   * @return A list of the {@code Polygon}s that were added to the map
   */
  @NonNull
  public List<Polygon> addPolygons(@NonNull List<PolygonOptions> polygonOptionsList) {
    return annotationManager.addPolygons(polygonOptionsList, this);
  }

  /**
   * Update a polygon on this map.
   *
   * @param polygon An updated polygon object
   */
  public void updatePolygon(Polygon polygon) {
    annotationManager.updatePolygon(polygon);
  }

  /**
   * <p>
   * Convenience method for removing a Marker from the map.
   * </p>
   * Calls removeAnnotation() internally.
   *
   * @param marker Marker to remove
   */
  public void removeMarker(@NonNull Marker marker) {
    annotationManager.removeAnnotation(marker);
  }

  /**
   * <p>
   * Convenience method for removing a Polyline from the map.
   * </p>
   * Calls removeAnnotation() internally.
   *
   * @param polyline Polyline to remove
   */
  public void removePolyline(@NonNull Polyline polyline) {
    annotationManager.removeAnnotation(polyline);
  }

  /**
   * <p>
   * Convenience method for removing a Polygon from the map.
   * </p>
   * Calls removeAnnotation() internally.
   *
   * @param polygon Polygon to remove
   */
  public void removePolygon(@NonNull Polygon polygon) {
    annotationManager.removeAnnotation(polygon);
  }

  /**
   * Removes an annotation from the map.
   *
   * @param annotation The annotation object to remove.
   */
  public void removeAnnotation(@NonNull Annotation annotation) {
    annotationManager.removeAnnotation(annotation);
  }

  /**
   * Removes an annotation from the map
   *
   * @param id The identifier associated to the annotation to be removed
   */
  public void removeAnnotation(long id) {
    annotationManager.removeAnnotation(id);
  }

  /**
   * Removes multiple annotations from the map.
   *
   * @param annotationList A list of annotation objects to remove.
   */
  public void removeAnnotations(@NonNull List<? extends Annotation> annotationList) {
    annotationManager.removeAnnotations(annotationList);
  }

  /**
   * Removes all annotations from the map.
   */
  public void removeAnnotations() {
    annotationManager.removeAnnotations();
  }

  /**
   * Removes all markers, polylines, polygons, overlays, etc from the map.
   */
  public void clear() {
    annotationManager.removeAnnotations();
  }

  /**
   * Return a annotation based on its id.
   *
   * @param id the id used to look up an annotation
   * @return An annotation with a matched id, null is returned if no match was found
   */
  @Nullable
  public Annotation getAnnotation(long id) {
    return annotationManager.getAnnotation(id);
  }

  /**
   * Returns a list of all the annotations on the map.
   *
   * @return A list of all the annotation objects. The returned object is a copy so modifying this
   * list will not update the map
   */
  @NonNull
  public List<Annotation> getAnnotations() {
    return annotationManager.getAnnotations();
  }

  /**
   * Returns a list of all the markers on the map.
   *
   * @return A list of all the markers objects. The returned object is a copy so modifying this
   * list will not update the map.
   */
  @NonNull
  public List<Marker> getMarkers() {
    return annotationManager.getMarkers();
  }

  /**
   * Returns a list of all the polygons on the map.
   *
   * @return A list of all the polygon objects. The returned object is a copy so modifying this
   * list will not update the map.
   */
  @NonNull
  public List<Polygon> getPolygons() {
    return annotationManager.getPolygons();
  }

  /**
   * Returns a list of all the polylines on the map.
   *
   * @return A list of all the polylines objects. The returned object is a copy so modifying this
   * list will not update the map.
   */
  @NonNull
  public List<Polyline> getPolylines() {
    return annotationManager.getPolylines();
  }

  /**
   * Sets a callback that's invoked when the user clicks on a marker.
   *
   * @param listener The callback that's invoked when the user clicks on a marker.
   *                 To unset the callback, use null.
   */
  public void setOnMarkerClickListener(@Nullable OnMarkerClickListener listener) {
    annotationManager.setOnMarkerClickListener(listener);
  }

  /**
   * Sets a callback that's invoked when the user clicks on a polygon.
   *
   * @param listener The callback that's invoked when the user clicks on a polygon.
   *                 To unset the callback, use null.
   */
  public void setOnPolygonClickListener(@Nullable OnPolygonClickListener listener) {
    annotationManager.setOnPolygonClickListener(listener);
  }

  /**
   * Sets a callback that's invoked when the user clicks on a polyline.
   *
   * @param listener The callback that's invoked when the user clicks on a polyline.
   *                 To unset the callback, use null.
   */
  public void setOnPolylineClickListener(@Nullable OnPolylineClickListener listener) {
    annotationManager.setOnPolylineClickListener(listener);
  }

  /**
   * <p>
   * Selects a marker. The selected marker will have it's info window opened.
   * Any other open info windows will be closed unless isAllowConcurrentMultipleOpenInfoWindows()
   * is true.
   * </p>
   * Selecting an already selected marker will have no effect.
   *
   * @param marker The marker to select.
   */
  public void selectMarker(@NonNull Marker marker) {
    if (marker == null) {
      Timber.w("marker was null, so just returning");
      return;
    }
    annotationManager.selectMarker(marker);
  }

  /**
   * Deselects any currently selected marker. All markers will have it's info window closed.
   */
  public void deselectMarkers() {
    annotationManager.deselectMarkers();
  }

  /**
   * Deselects a currently selected marker. The selected marker will have it's info window closed.
   *
   * @param marker the marker to deselect
   */
  public void deselectMarker(@NonNull Marker marker) {
    annotationManager.deselectMarker(marker);
  }

  /**
   * Gets the currently selected marker.
   *
   * @return The currently selected marker.
   */
  public List<Marker> getSelectedMarkers() {
    return annotationManager.getSelectedMarkers();
  }

  //
  // InfoWindow
  //

  /**
   * <p>
   * Sets a custom renderer for the contents of info window.
   * </p>
   * When set your callback is invoked when an info window is about to be shown. By returning
   * a custom {@link View}, the default info window will be replaced.
   *
   * @param infoWindowAdapter The callback to be invoked when an info window will be shown.
   *                          To unset the callback, use null.
   */
  public void setInfoWindowAdapter(@Nullable InfoWindowAdapter infoWindowAdapter) {
    annotationManager.getInfoWindowManager().setInfoWindowAdapter(infoWindowAdapter);
  }

  /**
   * Gets the callback to be invoked when an info window will be shown.
   *
   * @return The callback to be invoked when an info window will be shown.
   */
  @Nullable
  public InfoWindowAdapter getInfoWindowAdapter() {
    return annotationManager.getInfoWindowManager().getInfoWindowAdapter();
  }

  /**
   * Changes whether the map allows concurrent multiple infowindows to be shown.
   *
   * @param allow If true, map allows concurrent multiple infowindows to be shown.
   */
  public void setAllowConcurrentMultipleOpenInfoWindows(boolean allow) {
    annotationManager.getInfoWindowManager().setAllowConcurrentMultipleOpenInfoWindows(allow);
  }

  /**
   * Returns whether the map allows concurrent multiple infowindows to be shown.
   *
   * @return If true, map allows concurrent multiple infowindows to be shown.
   */
  public boolean isAllowConcurrentMultipleOpenInfoWindows() {
    return annotationManager.getInfoWindowManager().isAllowConcurrentMultipleOpenInfoWindows();
  }

  //
  // LatLngBounds
  //

  /**
   * Sets a LatLngBounds that constraints map transformations to this bounds.
   * <p>
   * Set to null to clear current bounds, newly set bounds will override previously set bounds.
   * </p>
   *
   * @param latLngBounds the bounds to constrain the map with
   */
  public void setLatLngBoundsForCameraTarget(@Nullable LatLngBounds latLngBounds) {
    nativeMapView.setLatLngBounds(latLngBounds);
  }

  /**
   * Get a camera position that fits a provided bounds and padding.
   *
   * @param latLngBounds the bounds to constrain the map with
   * @param padding      the padding to apply to the bounds
   * @return the camera position that fits the bounds and padding
   */
  public CameraPosition getCameraForLatLngBounds(@Nullable LatLngBounds latLngBounds, int[] padding) {
    // calculate and set additional bounds padding
    int[] mapPadding = getPadding();
    for (int i = 0; i < padding.length; i++) {
      padding[i] = mapPadding[i] + padding[i];
    }
    projection.setContentPadding(padding);

    // get padded camera position from LatLngBounds
    CameraPosition cameraPosition = nativeMapView.getCameraForLatLngBounds(latLngBounds);

    // reset map padding
    setPadding(mapPadding);
    return cameraPosition;
  }

  /**
   * Get a camera position that fits a provided shape with a given bearing and padding.
   *
   * @param geometry the geometry to constrain the map with
   * @param bearing  the bearing at which to compute the geometry's bounds
   * @param padding  the padding to apply to the bounds
   * @return the camera position that fits the bounds and padding
   */
  public CameraPosition getCameraForGeometry(Geometry geometry, double bearing, int[] padding) {
    // calculate and set additional bounds padding
    int[] mapPadding = getPadding();
    for (int i = 0; i < padding.length; i++) {
      padding[i] = mapPadding[i] + padding[i];
    }
    projection.setContentPadding(padding);

    // get padded camera position from LatLngBounds
    CameraPosition cameraPosition = nativeMapView.getCameraForGeometry(geometry, bearing);

    // reset map padding
    setPadding(mapPadding);
    return cameraPosition;
  }

  //
  // Padding
  //

  /**
   * <p>
   * Sets the distance from the edges of the map view’s frame to the edges of the map
   * view’s logical viewport.
   * </p>
   * <p>
   * When the value of this property is equal to {0,0,0,0}, viewport
   * properties such as `centerCoordinate` assume a viewport that matches the map
   * view’s frame. Otherwise, those properties are inset, excluding part of the
   * frame from the viewport. For instance, if the only the top edge is inset, the
   * map center is effectively shifted downward.
   * </p>
   *
   * @param left   The left margin in pixels.
   * @param top    The top margin in pixels.
   * @param right  The right margin in pixels.
   * @param bottom The bottom margin in pixels.
   */
  public void setPadding(int left, int top, int right, int bottom) {
    setPadding(new int[] {left, top, right, bottom});
  }

  private void setPadding(int[] padding) {
    projection.setContentPadding(padding);
    uiSettings.invalidate();
  }

  /**
   * Returns the current configured content padding on map view.
   *
   * @return An array with length 4 in the LTRB order.
   */
  public int[] getPadding() {
    return projection.getContentPadding();
  }

  //
  // Map events
  //

  /**
   * Sets a callback that's invoked on every change in camera position.
   *
   * @param listener The callback that's invoked on every camera change position.
   *                 To unset the callback, use null.
   */
  @Deprecated
  public void setOnCameraChangeListener(@Nullable OnCameraChangeListener listener) {
    transform.setOnCameraChangeListener(listener);
  }

  /**
   * Sets a callback that is invoked when camera movement has ended.
   *
   * @param listener the listener to notify
   * @deprecated use {@link #addOnCameraIdleListener(OnCameraIdleListener)}
   * and {@link #removeOnCameraIdleListener(OnCameraIdleListener)} instead
   */
  @Deprecated
  public void setOnCameraIdleListener(@Nullable OnCameraIdleListener listener) {
    cameraChangeDispatcher.setOnCameraIdleListener(listener);
  }

  /**
   * Adds a callback that is invoked when camera movement has ended.
   *
   * @param listener the listener to notify
   */
  public void addOnCameraIdleListener(@Nullable OnCameraIdleListener listener) {
    cameraChangeDispatcher.addOnCameraIdleListener(listener);
  }

  /**
   * Removes a callback that is invoked when camera movement has ended.
   *
   * @param listener the listener to remove
   */
  public void removeOnCameraIdleListener(@Nullable OnCameraIdleListener listener) {
    cameraChangeDispatcher.removeOnCameraIdleListener(listener);
  }

  /**
   * Sets a callback that is invoked when camera movement was cancelled.
   *
   * @param listener the listener to notify
   * @deprecated use {@link #addOnCameraMoveCancelListener(OnCameraMoveCanceledListener)} and
   * {@link #removeOnCameraMoveCancelListener(OnCameraMoveCanceledListener)} instead
   */
  @Deprecated
  public void setOnCameraMoveCancelListener(@Nullable OnCameraMoveCanceledListener listener) {
    cameraChangeDispatcher.setOnCameraMoveCanceledListener(listener);
  }

  /**
   * Adds a callback that is invoked when camera movement was cancelled.
   *
   * @param listener the listener to notify
   */
  public void addOnCameraMoveCancelListener(@Nullable OnCameraMoveCanceledListener listener) {
    cameraChangeDispatcher.addOnCameraMoveCancelListener(listener);
  }

  /**
   * Removes a callback that is invoked when camera movement was cancelled.
   *
   * @param listener the listener to remove
   */
  public void removeOnCameraMoveCancelListener(@Nullable OnCameraMoveCanceledListener listener) {
    cameraChangeDispatcher.removeOnCameraMoveCancelListener(listener);
  }

  /**
   * Sets a callback that is invoked when camera movement has started.
   *
   * @param listener the listener to notify
   * @deprecated use {@link #addOnCameraMoveStartedListener(OnCameraMoveStartedListener)} and
   * {@link #removeOnCameraMoveStartedListener(OnCameraMoveStartedListener)} instead
   */
  @Deprecated
  public void setOnCameraMoveStartedListener(@Nullable OnCameraMoveStartedListener listener) {
    cameraChangeDispatcher.setOnCameraMoveStartedListener(listener);
  }

  /**
   * Adds a callback that is invoked when camera movement has started.
   *
   * @param listener the listener to notify
   */
  public void addOnCameraMoveStartedListener(@Nullable OnCameraMoveStartedListener listener) {
    cameraChangeDispatcher.addOnCameraMoveStartedListener(listener);
  }

  /**
   * Removes a callback that is invoked when camera movement has started.
   *
   * @param listener the listener to remove
   */
  public void removeOnCameraMoveStartedListener(@Nullable OnCameraMoveStartedListener listener) {
    cameraChangeDispatcher.removeOnCameraMoveStartedListener(listener);
  }

  /**
   * Sets a callback that is invoked when camera position changes.
   *
   * @param listener the listener to notify
   * @deprecated use {@link #addOnCameraMoveListener(OnCameraMoveListener)} and
   * {@link #removeOnCameraMoveListener(OnCameraMoveListener)}instead
   */
  @Deprecated
  public void setOnCameraMoveListener(@Nullable OnCameraMoveListener listener) {
    cameraChangeDispatcher.setOnCameraMoveListener(listener);
  }

  /**
   * Adds a callback that is invoked when camera position changes.
   *
   * @param listener the listener to notify
   */
  public void addOnCameraMoveListener(@Nullable OnCameraMoveListener listener) {
    cameraChangeDispatcher.addOnCameraMoveListener(listener);
  }

  /**
   * Removes a callback that is invoked when camera position changes.
   *
   * @param listener the listener to remove
   */
  public void removeOnCameraMoveListener(@Nullable OnCameraMoveListener listener) {
    cameraChangeDispatcher.removeOnCameraMoveListener(listener);
  }

  /**
   * Sets a callback that's invoked on every frame rendered to the map view.
   *
   * @param listener The callback that's invoked on every frame rendered to the map view.
   *                 To unset the callback, use null.
   */
  public void setOnFpsChangedListener(@Nullable OnFpsChangedListener listener) {
    onFpsChangedListener = listener;
    nativeMapView.setOnFpsChangedListener(listener);
  }

  // used by MapView
  OnFpsChangedListener getOnFpsChangedListener() {
    return onFpsChangedListener;
  }

  /**
   * Sets a callback that's invoked when the map is scrolled.
   *
   * @param listener The callback that's invoked when the map is scrolled.
   *                 To unset the callback, use null.
   * @deprecated Use {@link #addOnScrollListener(OnScrollListener)} instead.
   */
  @Deprecated
  public void setOnScrollListener(@Nullable OnScrollListener listener) {
    onRegisterTouchListener.onSetScrollListener(listener);
  }

  /**
   * Adds a callback that's invoked when the map is scrolled.
   *
   * @param listener The callback that's invoked when the map is scrolled.
   *                 To unset the callback, use null.
   */
  public void addOnScrollListener(@Nullable OnScrollListener listener) {
    onRegisterTouchListener.onAddScrollListener(listener);
  }

  /**
   * Removes a callback that's invoked when the map is scrolled.
   *
   * @param listener The callback that's invoked when the map is scrolled.
   *                 To unset the callback, use null.
   */
  public void removeOnScrollListener(@Nullable OnScrollListener listener) {
    onRegisterTouchListener.onRemoveScrollListener(listener);
  }

  /**
   * Sets a callback that's invoked when the map is flinged.
   *
   * @param listener The callback that's invoked when the map is flinged.
   *                 To unset the callback, use null.
   * @deprecated Use {@link #addOnFlingListener(OnFlingListener)} instead.
   */
  @Deprecated
  public void setOnFlingListener(@Nullable OnFlingListener listener) {
    onRegisterTouchListener.onSetFlingListener(listener);
  }

  /**
   * Adds a callback that's invoked when the map is flinged.
   *
   * @param listener The callback that's invoked when the map is flinged.
   *                 To unset the callback, use null.
   */
  public void addOnFlingListener(@Nullable OnFlingListener listener) {
    onRegisterTouchListener.onAddFlingListener(listener);
  }

  /**
   * Removes a callback that's invoked when the map is flinged.
   *
   * @param listener The callback that's invoked when the map is flinged.
   *                 To unset the callback, use null.
   */
  public void removeOnFlingListener(@Nullable OnFlingListener listener) {
    onRegisterTouchListener.onRemoveFlingListener(listener);
  }

  /**
   * Sets a callback that's invoked when the user clicks on the map view.
   *
   * @param listener The callback that's invoked when the user clicks on the map view.
   *                 To unset the callback, use null.
   * @deprecated Use {@link #addOnMapClickListener(OnMapClickListener)} instead.
   */
  @Deprecated
  public void setOnMapClickListener(@Nullable OnMapClickListener listener) {
    onRegisterTouchListener.onSetMapClickListener(listener);
  }

  /**
   * Adds a callback that's invoked when the user clicks on the map view.
   *
   * @param listener The callback that's invoked when the user clicks on the map view.
   *                 To unset the callback, use null.
   */
  public void addOnMapClickListener(@Nullable OnMapClickListener listener) {
    onRegisterTouchListener.onAddMapClickListener(listener);
  }

  /**
   * Removes a callback that's invoked when the user clicks on the map view.
   *
   * @param listener The callback that's invoked when the user clicks on the map view.
   *                 To unset the callback, use null.
   */
  public void removeOnMapClickListener(@Nullable OnMapClickListener listener) {
    onRegisterTouchListener.onRemoveMapClickListener(listener);
  }

  /**
   * Sets a callback that's invoked when the user long clicks on the map view.
   *
   * @param listener The callback that's invoked when the user long clicks on the map view.
   *                 To unset the callback, use null.
   * @deprecated Use {@link #addOnMapLongClickListener(OnMapLongClickListener)} instead.
   */
  @Deprecated
  public void setOnMapLongClickListener(@Nullable OnMapLongClickListener listener) {
    onRegisterTouchListener.onSetMapLongClickListener(listener);
  }

  /**
   * Adds a callback that's invoked when the user long clicks on the map view.
   *
   * @param listener The callback that's invoked when the user long clicks on the map view.
   *                 To unset the callback, use null.
   */
  public void addOnMapLongClickListener(@Nullable OnMapLongClickListener listener) {
    onRegisterTouchListener.onAddMapLongClickListener(listener);
  }

  /**
   * Removes a callback that's invoked when the user long clicks on the map view.
   *
   * @param listener The callback that's invoked when the user long clicks on the map view.
   *                 To unset the callback, use null.
   */
  public void removeOnMapLongClickListener(@Nullable OnMapLongClickListener listener) {
    onRegisterTouchListener.onRemoveMapLongClickListener(listener);
  }

  /**
   * Sets a callback that's invoked when the user clicks on an info window.
   *
   * @param listener The callback that's invoked when the user clicks on an info window.
   *                 To unset the callback, use null.
   */
  public void setOnInfoWindowClickListener(@Nullable OnInfoWindowClickListener listener) {
    annotationManager.getInfoWindowManager().setOnInfoWindowClickListener(listener);
  }

  /**
   * Return the InfoWindow click listener
   *
   * @return Current active InfoWindow Click Listener
   */
  public OnInfoWindowClickListener getOnInfoWindowClickListener() {
    return annotationManager.getInfoWindowManager().getOnInfoWindowClickListener();
  }

  /**
   * Sets a callback that's invoked when a marker's info window is long pressed.
   *
   * @param listener The callback that's invoked when a marker's info window is long pressed. To unset the callback,
   *                 use null.
   */
  public void setOnInfoWindowLongClickListener(@Nullable OnInfoWindowLongClickListener
                                                 listener) {
    annotationManager.getInfoWindowManager().setOnInfoWindowLongClickListener(listener);
  }

  /**
   * Return the InfoWindow long click listener
   *
   * @return Current active InfoWindow long Click Listener
   */
  public OnInfoWindowLongClickListener getOnInfoWindowLongClickListener() {
    return annotationManager.getInfoWindowManager().getOnInfoWindowLongClickListener();
  }

  /**
   * Set an callback to be invoked when an InfoWindow closes.
   *
   * @param listener callback invoked when an InfoWindow closes
   */
  public void setOnInfoWindowCloseListener(@Nullable OnInfoWindowCloseListener listener) {
    annotationManager.getInfoWindowManager().setOnInfoWindowCloseListener(listener);
  }

  /**
   * Return the InfoWindow close listener
   *
   * @return Current active InfoWindow Close Listener
   */
  public OnInfoWindowCloseListener getOnInfoWindowCloseListener() {
    return annotationManager.getInfoWindowManager().getOnInfoWindowCloseListener();
  }

  //
  // Invalidate
  //

  /**
   * Takes a snapshot of the map.
   *
   * @param callback Callback method invoked when the snapshot is taken.
   */
  public void snapshot(@NonNull SnapshotReadyCallback callback) {
    nativeMapView.addSnapshotCallback(callback);
  }

  /**
   * Queries the map for rendered features
   *
   * @param coordinates the point to query
   * @param layerIds    optionally - only query these layers
   * @return the list of feature
   */
  @NonNull
  public List<Feature> queryRenderedFeatures(@NonNull PointF coordinates, @Nullable String...
    layerIds) {
    return nativeMapView.queryRenderedFeatures(coordinates, layerIds, null);
  }

  /**
   * Queries the map for rendered features
   *
   * @param coordinates the point to query
   * @param filter      filters the returned features
   * @param layerIds    optionally - only query these layers
   * @return the list of feature
   */
  @NonNull
  public List<Feature> queryRenderedFeatures(@NonNull PointF coordinates,
                                             @Nullable Filter.Statement filter,
                                             @Nullable String... layerIds) {
    return nativeMapView.queryRenderedFeatures(coordinates, layerIds, filter);
  }

  /**
   * Queries the map for rendered features
   *
   * @param coordinates the box to query
   * @param layerIds    optionally - only query these layers
   * @return the list of feature
   */
  @NonNull
  public List<Feature> queryRenderedFeatures(@NonNull RectF coordinates,
                                             @Nullable String... layerIds) {
    return nativeMapView.queryRenderedFeatures(coordinates, layerIds, null);
  }

  /**
   * Queries the map for rendered features
   *
   * @param coordinates the box to query
   * @param filter      filters the returned features
   * @param layerIds    optionally - only query these layers
   * @return the list of feature
   */
  @NonNull
  public List<Feature> queryRenderedFeatures(@NonNull RectF coordinates,
                                             @Nullable Filter.Statement filter,
                                             @Nullable String... layerIds) {
    return nativeMapView.queryRenderedFeatures(coordinates, layerIds, filter);
  }

  FocalPointChangeListener createFocalPointChangeListener() {
    return new FocalPointChangeListener() {
      @Override
      public void onFocalPointChanged(PointF pointF) {
        focalPoint = pointF;
      }
    };
  }

  //
  // Interfaces
  //

  /**
   * Interface definition for a callback to be invoked when the map is flinged.
   *
   * @see MapboxMap#setOnFlingListener(OnFlingListener)
   */
  public interface OnFlingListener {
    /**
     * Called when the map is flinged.
     */
    void onFling();
  }

  /**
   * Interface definition for a callback to be invoked when the map is scrolled.
   *
   * @see MapboxMap#setOnScrollListener(OnScrollListener)
   */
  public interface OnScrollListener {
    /**
     * Called when the map is scrolled.
     */
    void onScroll();
  }

  /**
   * Interface definition for a callback to be invoked when the camera changes position.
   *
   * @deprecated Replaced by {@link MapboxMap.OnCameraMoveStartedListener}, {@link MapboxMap.OnCameraMoveListener} and
   * {@link MapboxMap.OnCameraIdleListener}. The order in which the deprecated onCameraChange method will be called in
   * relation to the methods in the new camera change listeners is undefined.
   */
  @Deprecated
  public interface OnCameraChangeListener {
    /**
     * Called after the camera position has changed. During an animation,
     * this listener may not be notified of intermediate camera positions.
     * It is always called for the final position in the animation.
     *
     * @param position The CameraPosition at the end of the last camera change.
     */
    void onCameraChange(CameraPosition position);
  }

  /**
   * Interface definition for a callback to be invoked for when the camera motion starts.
   */
  public interface OnCameraMoveStartedListener {
    int REASON_API_GESTURE = 1;
    int REASON_DEVELOPER_ANIMATION = 2;
    int REASON_API_ANIMATION = 3;

    /**
     * Called when the camera starts moving after it has been idle or when the reason for camera motion has changed.
     *
     * @param reason the reason for the camera change
     */
    void onCameraMoveStarted(int reason);
  }

  /**
   * Interface definition for a callback to be invoked for when the camera changes position.
   */
  public interface OnCameraMoveListener {
    /**
     * Called repeatedly as the camera continues to move after an onCameraMoveStarted call.
     * This may be called as often as once every frame and should not perform expensive operations.
     */
    void onCameraMove();
  }

  /**
   * Interface definition for a callback to be invoked for when the camera's motion has been stopped or when the camera
   * starts moving for a new reason.
   */
  public interface OnCameraMoveCanceledListener {
    /**
     * Called when the developer explicitly calls the cancelTransitions() method or if the reason for camera motion has
     * changed before the onCameraIdle had a chance to fire after the previous animation.
     * Do not update or animate the camera from within this method.
     */
    void onCameraMoveCanceled();
  }

  /**
   * Interface definition for a callback to be invoked for when camera movement has ended.
   */
  public interface OnCameraIdleListener {
    /**
     * Called when camera movement has ended.
     */
    void onCameraIdle();
  }

  /**
   * Interface definition for a callback to be invoked for when the compass is animating.
   */
  public interface OnCompassAnimationListener {
    /**
     * Called repeatedly as the compass continues to move after clicking on it.
     */
    void onCompassAnimation();

    /**
     * Called when compass animation has ended.
     */
    void onCompassAnimationFinished();
  }

  /**
   * Interface definition for a callback to be invoked when a frame is rendered to the map view.
   *
   * @see MapboxMap#setOnFpsChangedListener(OnFpsChangedListener)
   */
  public interface OnFpsChangedListener {
    /**
     * Called for every frame rendered to the map view.
     *
     * @param fps The average number of frames rendered over the last second.
     */
    void onFpsChanged(double fps);
  }

  /**
   * Interface definition for a callback to be invoked when a user registers an listener that is
   * related to touch and click events.
   */
  interface OnRegisterTouchListener {
    void onSetMapClickListener(OnMapClickListener listener);

    void onAddMapClickListener(OnMapClickListener listener);

    void onRemoveMapClickListener(OnMapClickListener listener);

    void onSetMapLongClickListener(OnMapLongClickListener listener);

    void onAddMapLongClickListener(OnMapLongClickListener listener);

    void onRemoveMapLongClickListener(OnMapLongClickListener listener);

    void onSetScrollListener(OnScrollListener listener);

    void onAddScrollListener(OnScrollListener listener);

    void onRemoveScrollListener(OnScrollListener listener);

    void onSetFlingListener(OnFlingListener listener);

    void onAddFlingListener(OnFlingListener listener);

    void onRemoveFlingListener(OnFlingListener listener);
  }

  /**
   * Interface definition for a callback to be invoked when the user clicks on the map view.
   *
   * @see MapboxMap#setOnMapClickListener(OnMapClickListener)
   */
  public interface OnMapClickListener {
    /**
     * Called when the user clicks on the map view.
     *
     * @param point The projected map coordinate the user clicked on.
     */
    void onMapClick(@NonNull LatLng point);
  }

  /**
   * Interface definition for a callback to be invoked when the user long clicks on the map view.
   *
   * @see MapboxMap#setOnMapLongClickListener(OnMapLongClickListener)
   */
  public interface OnMapLongClickListener {
    /**
     * Called when the user long clicks on the map view.
     *
     * @param point The projected map coordinate the user long clicked on.
     */
    void onMapLongClick(@NonNull LatLng point);
  }

  /**
   * Interface definition for a callback to be invoked when the user clicks on a marker.
   *
   * @see MapboxMap#setOnMarkerClickListener(OnMarkerClickListener)
   */
  public interface OnMarkerClickListener {
    /**
     * Called when the user clicks on a marker.
     *
     * @param marker The marker the user clicked on.
     * @return If true the listener has consumed the event and the info window will not be shown.
     */
    boolean onMarkerClick(@NonNull Marker marker);
  }

  /**
   * Interface definition for a callback to be invoked when the user clicks on a polygon.
   *
   * @see MapboxMap#setOnPolygonClickListener(OnPolygonClickListener)
   */
  public interface OnPolygonClickListener {
    /**
     * Called when the user clicks on a polygon.
     *
     * @param polygon The polygon the user clicked on.
     */
    void onPolygonClick(@NonNull Polygon polygon);
  }

  /**
   * Interface definition for a callback to be invoked when the user clicks on a polyline.
   *
   * @see MapboxMap#setOnPolylineClickListener(OnPolylineClickListener)
   */
  public interface OnPolylineClickListener {
    /**
     * Called when the user clicks on a polyline.
     *
     * @param polyline The polyline the user clicked on.
     */
    void onPolylineClick(@NonNull Polyline polyline);
  }

  /**
   * Interface definition for a callback to be invoked when the user clicks on an info window.
   *
   * @see MapboxMap#setOnInfoWindowClickListener(OnInfoWindowClickListener)
   */
  public interface OnInfoWindowClickListener {
    /**
     * Called when the user clicks on an info window.
     *
     * @param marker The marker of the info window the user clicked on.
     * @return If true the listener has consumed the event and the info window will not be closed.
     */
    boolean onInfoWindowClick(@NonNull Marker marker);
  }

  /**
   * Interface definition for a callback to be invoked when the user long presses on a marker's info window.
   *
   * @see MapboxMap#setOnInfoWindowClickListener(OnInfoWindowClickListener)
   */
  public interface OnInfoWindowLongClickListener {

    /**
     * Called when the user makes a long-press gesture on the marker's info window.
     *
     * @param marker The marker were the info window is attached to
     */
    void onInfoWindowLongClick(Marker marker);
  }

  /**
   * Interface definition for a callback to be invoked when a marker's info window is closed.
   *
   * @see MapboxMap#setOnInfoWindowCloseListener(OnInfoWindowCloseListener)
   */
  public interface OnInfoWindowCloseListener {

    /**
     * Called when the marker's info window is closed.
     *
     * @param marker The marker of the info window that was closed.
     */
    void onInfoWindowClose(Marker marker);
  }

  /**
   * Interface definition for a callback to be invoked when an info window will be shown.
   *
   * @see MapboxMap#setInfoWindowAdapter(InfoWindowAdapter)
   */
  public interface InfoWindowAdapter {
    /**
     * Called when an info window will be shown as a result of a marker click.
     *
     * @param marker The marker the user clicked on.
     * @return View to be shown as a info window. If null is returned the default
     * info window will be shown.
     */
    @Nullable
    View getInfoWindow(@NonNull Marker marker);
  }

  /**
   * Interface definition for a callback to be invoked when a task is complete or cancelled.
   */
  public interface CancelableCallback {
    /**
     * Invoked when a task is cancelled.
     */
    void onCancel();

    /**
     * Invoked when a task is complete.
     */
    void onFinish();
  }

  /**
   * Interface definition for a callback to be invoked when the snapshot has been taken.
   */
  public interface SnapshotReadyCallback {
    /**
     * Invoked when the snapshot has been taken.
     *
     * @param snapshot the snapshot bitmap
     */
    void onSnapshotReady(Bitmap snapshot);
  }

  /**
   * Interface definition for a callback to be invoked when the style has finished loading.
   */
  public interface OnStyleLoadedListener {
    /**
     * Invoked when the style has finished loading
     *
     * @param style the style that has been loaded
     */
    void onStyleLoaded(String style);
  }

  //
  // Used for instrumentation testing
  //
  Transform getTransform() {
    return transform;
  }
}
