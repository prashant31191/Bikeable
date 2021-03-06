package com.nnys.bikeable;

import android.util.Log;

import com.google.android.gms.location.places.AutocompletePrediction;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.maps.DirectionsApi;
import com.google.maps.GeoApiContext;
import com.google.maps.PlacesApi;
import com.google.maps.model.DirectionsRoute;
import com.google.maps.model.PlaceDetails;
import com.google.maps.model.TravelMode;

/**
 * This class manages the directions retrieval from google maps direction API and updating the map
 * with origin and destination markers.
 */
public class DirectionsManager {

    private GeoApiContext context;
    private GoogleMap mMap;
    private DirectionsRoute[] calculatedRoutes;
    private LatLngBounds.Builder directionBoundsBuilder;
    private LatLngBounds directionBounds;

    private com.google.android.gms.maps.model.LatLng fromLatLngCurr, toLatLngCurr, fromLatLngNew, toLatLngNew;
    private String fromTitleCurr, toTitleCurr, fromTitleNew, toTitleNew;

    private Marker fromMarkerCurr, fromMarkerNew, toMarkerCurr, toMarkerNew;

    private AutocompletePrediction predictionFrom, predictionTo;

    public DirectionsManager(GeoApiContext context, GoogleMap mMap){
        this.context = context;
        if (mMap != null)
            this.mMap = mMap;
        calculatedRoutes = null;
        directionBounds = null;
        predictionFrom = null;
        predictionTo = null;
    }

    public void getDirections() {

        this.fromLatLngCurr = fromLatLngNew;
        this.toLatLngCurr = toLatLngNew;
        this.fromTitleCurr = fromTitleNew;
        this.toTitleCurr = toTitleNew;

        try {
            calculatedRoutes = DirectionsApi.newRequest(context)
                    .alternatives(true)
                    .mode(TravelMode.WALKING)
                    .origin(MapUtils.getModelLatLngFromGms(fromLatLngCurr))
                    .destination(MapUtils.getModelLatLngFromGms(toLatLngCurr))
                    .await();

        } catch (Exception e) {
            e.printStackTrace();
        }

        clearMarkersFromMap();
        drawRouteMarkers();
        updateBounds();

    }

    public void addCurrentSearchTargetToHistory(SearchHistoryCollector historyCollector) {
        if ( predictionTo != null ) {
            historyCollector.addPredictionToHistory(predictionTo);
        }
    }

    protected void drawRouteMarkers(){
        if (fromMarkerCurr != null){
            fromMarkerCurr.remove();
        }
        if (toMarkerCurr != null){
            toMarkerCurr.remove();
        }

        fromMarkerCurr = mMap.addMarker(
                    new MarkerOptions()
                            .title(fromTitleCurr)
                            .position(fromLatLngCurr)
        );

        toMarkerCurr = mMap.addMarker(
                    new MarkerOptions()
                        .title(toTitleCurr)
                        .position(toLatLngCurr)
        );
     }

    protected void clearMarkersFromMap() {
        if (fromMarkerNew != null)
            fromMarkerNew.remove();
        if (toMarkerNew != null)
            toMarkerNew.remove();
    }

    public DirectionsRoute[] getCalculatedRoutes(){
        return calculatedRoutes;
    }

    public LatLngBounds getDirectionBounds(){
        return directionBounds;
    }

    public LatLng getFromLatLngCurr(){ return fromLatLngCurr; }

    public LatLng getToLatLngCurr(){
        return toLatLngCurr;
    }

    public void setToMarkerNew(Marker toMarkerNew) {
        this.toMarkerNew = toMarkerNew;
    }

    public void setFromMarkerNew(Marker fromMarkerNew) {
        this.fromMarkerNew = fromMarkerNew;
    }

    public Marker getFromMarkerNew() {
        return fromMarkerNew;
    }

    public Marker getToMarkerNew() {
        return toMarkerNew;
    }

    public void setMap(GoogleMap mMap) {
        this.mMap = mMap;
    }

    public GoogleMap getMap() {
        return mMap;
    }

    private void updateBounds(){
        directionBoundsBuilder = new LatLngBounds.Builder();
        directionBoundsBuilder.include(fromLatLngCurr);
        directionBoundsBuilder.include(toLatLngCurr);
        directionBounds = directionBoundsBuilder.build();
    }

    public void setNewMarkerByPlacePrediction(boolean isFrom, AutocompletePrediction prediction) {

        PlaceDetails placeDetails = null;
        try {
            Log.i("INFO:", String.format("attemp to get place details: place ID: %s", prediction.getPlaceId()));
            placeDetails = PlacesApi.placeDetails(context, prediction.getPlaceId()).await();
        } catch (Exception e) {
            e.printStackTrace(); // todo: handle this properly
        }
        LatLng markerLatLng = MapUtils.getGmsLatLngFromModel(placeDetails.geometry.location);
        String markerTitle = prediction.getDescription();
        Marker newMarker = mMap.addMarker(new MarkerOptions().position(markerLatLng).title(markerTitle));
        if (isFrom){
            if (this.getFromMarkerNew() != null){
                this.getFromMarkerNew().remove();
            }
            if (this.fromMarkerCurr != null){
                this.fromMarkerCurr.remove();
            }
            this.setFromMarkerNew(newMarker);
            this.fromLatLngNew = markerLatLng;
            this.fromTitleNew = prediction.getDescription();
            predictionFrom = prediction;
        }
        else{ // isTo
            if (this.getToMarkerNew() != null){
                this.getToMarkerNew().remove();
            }
            if (this.toMarkerCurr != null){
                this.toMarkerCurr.remove();
            }
            this.setToMarkerNew(newMarker);
            this.toLatLngNew = markerLatLng;
            this.toTitleNew = prediction.getDescription();
            predictionTo = prediction;
        }
    }

    public void setFromCurrLocation(LatLng currLatLng, CustomAutoCompletePrediction prediction){
        if (this.fromMarkerNew != null){
            this.fromMarkerNew.remove();
        }
        if (this.fromMarkerCurr != null){
            this.fromMarkerCurr.remove();
        }
        this.fromLatLngNew = currLatLng;
        this.fromTitleNew = prediction.getDescription();
        predictionFrom = prediction;
    }

    public void setNewMarkerByCustomPrediction(boolean isFrom, LatLng markerLatLng, CustomAutoCompletePrediction prediction) {
        // for markers selected on map and for current location
        Marker newMarker = mMap.addMarker(new MarkerOptions().position(markerLatLng));
        if (isFrom){
            if (this.fromMarkerNew != null){
                this.fromMarkerNew.remove();
            }
            if (this.fromMarkerCurr != null){
                this.fromMarkerCurr.remove();
            }
            newMarker.setTitle(prediction.getDescription());
            this.setFromMarkerNew(newMarker);
            this.fromLatLngNew = markerLatLng;
            this.fromTitleNew = prediction.getDescription();
            predictionFrom = prediction;
        }
        else {  // isTo
            if (this.toMarkerNew != null){
                this.toMarkerNew.remove();
            }
            if (this.toMarkerCurr != null){
                this.toMarkerCurr.remove();
            }
            newMarker.setTitle(prediction.getDescription());
            this.setToMarkerNew(newMarker);
            this.toLatLngNew = markerLatLng;
            this.toTitleNew = prediction.getDescription();
            predictionTo = prediction;
        }
    }

    public void clearNewMarker(boolean isFrom) {
        if (isFrom && fromMarkerNew != null){
            fromMarkerNew.remove();
            fromMarkerNew = null;
        }
        else if (!isFrom && toMarkerNew != null){
            toMarkerNew.remove();
            toMarkerNew = null;
        }
    }

    public void setFromLatLngNew(LatLng fromLatLngCurr) {
        this.fromLatLngNew = fromLatLngCurr;
    }


}
