package com.mapbox.mapboxsdk.offline;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.gson.GsonBuilder;
import com.mapbox.geojson.BoundingBox;
import com.mapbox.geojson.Geometry;
import com.mapbox.geojson.Point;
import com.mapbox.geojson.Polygon;
import com.mapbox.geojson.gson.BoundingBoxDeserializer;
import com.mapbox.geojson.gson.GeoJsonAdapterFactory;
import com.mapbox.geojson.gson.GeometryDeserializer;
import com.mapbox.geojson.gson.PointDeserializer;
import com.mapbox.mapboxsdk.geometry.LatLngBounds;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * An offline region defined by a style URL, geographic bounding box, zoom range, and
 * device pixel ratio.
 * <p>
 * Both minZoom and maxZoom must be ≥ 0, and maxZoom must be ≥ minZoom.
 * <p>
 * maxZoom may be ∞, in which case for each tile source, the region will include
 * tiles from minZoom up to the maximum zoom level provided by that source.
 * <p>
 * pixelRatio must be ≥ 0 and should typically be 1.0 or 2.0.
 */
public class OfflineRegionDefinition implements Parcelable {

  private String styleURL;
  private Geometry geometry;
  private double minZoom;
  private double maxZoom;
  private float pixelRatio;

  /**
   * Constructor to create an OfflineTilePyramidDefinition from parameters.
   *
   * @param styleURL   the style
   * @param bounds     the bounds
   * @param minZoom    min zoom
   * @param maxZoom    max zoom
   * @param pixelRatio pixel ratio of the device
   */
  public OfflineRegionDefinition(
    String styleURL, LatLngBounds bounds, double minZoom, double maxZoom, float pixelRatio) {
    // Note: Also used in JNI
    this.styleURL = styleURL;
    this.geometry = Polygon.fromLngLats(
      Collections.singletonList(
        Arrays.asList(
          Point.fromLngLat(bounds.getLonWest(), bounds.getLatSouth()),
          Point.fromLngLat(bounds.getLonEast(), bounds.getLatSouth()),
          Point.fromLngLat(bounds.getLonEast(), bounds.getLatNorth()),
          Point.fromLngLat(bounds.getLonWest(), bounds.getLatNorth()),
          Point.fromLngLat(bounds.getLonWest(), bounds.getLatSouth()))
      )
    );
    this.minZoom = minZoom;
    this.maxZoom = maxZoom;
    this.pixelRatio = pixelRatio;
  }

  /**
   * Constructor to create an OfflineTilePyramidDefinition from parameters.
   *
   * @param styleURL   the style
   * @param geometry   the geometry
   * @param minZoom    min zoom
   * @param maxZoom    max zoom
   * @param pixelRatio pixel ratio of the device
   */
  public OfflineRegionDefinition(
    String styleURL, Geometry geometry, double minZoom, double maxZoom, float pixelRatio) {
    // Note: Also used in JNI
    this.styleURL = styleURL;
    this.geometry = geometry;
    this.minZoom = minZoom;
    this.maxZoom = maxZoom;
    this.pixelRatio = pixelRatio;
  }

  private static Geometry fromJson(String json) {
    GsonBuilder gson = new GsonBuilder();
    gson.registerTypeAdapterFactory(GeoJsonAdapterFactory.create());
    gson.registerTypeAdapter(Point.class, new PointDeserializer());
    gson.registerTypeAdapter(BoundingBox.class, new BoundingBoxDeserializer());
    gson.registerTypeAdapter(Geometry.class, new GeometryDeserializer());
    return gson.create().fromJson(json, Geometry.class);
  }

  /**
   * Constructor to create an OfflineTilePyramidDefinition from a Parcel.
   *
   * @param parcel the parcel to create the OfflineTilePyramidDefinition from
   */
  public OfflineRegionDefinition(Parcel parcel) {
    this.styleURL = parcel.readString();
    this.geometry = fromJson(parcel.readString());
    this.minZoom = parcel.readDouble();
    this.maxZoom = parcel.readDouble();
    this.pixelRatio = parcel.readFloat();
  }

  /*
   * Getters
   */

  public String getStyleURL() {
    return styleURL;
  }

  public Geometry getGeometry() {
    return geometry;
  }

  public double getMinZoom() {
    return minZoom;
  }

  public double getMaxZoom() {
    return maxZoom;
  }

  public float getPixelRatio() {
    return pixelRatio;
  }

  /*
   * Parceable
   */

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(styleURL);
    dest.writeString(geometry.toJson());
    dest.writeDouble(minZoom);
    dest.writeDouble(maxZoom);
    dest.writeFloat(pixelRatio);
  }

  public static final Parcelable.Creator CREATOR = new Parcelable.Creator() {
    public OfflineRegionDefinition createFromParcel(Parcel in) {
      return new OfflineRegionDefinition(in);
    }

    public OfflineRegionDefinition[] newArray(int size) {
      return new OfflineRegionDefinition[size];
    }
  };
}
