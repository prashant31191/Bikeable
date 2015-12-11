package com.nnys.bikeable;

import android.location.Location;
import android.os.Bundle;
import android.os.StrictMode;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.nnys.bikeable.MyLocation.LocationResult;
import com.google.android.gms.location.places.AutocompletePrediction;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.maps.GeoApiContext;
import com.google.maps.model.DirectionsRoute;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.maps.model.ElevationResult;
import com.jjoe64.graphview.GraphView;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;

import static java.lang.Thread.sleep;


public class CentralActivity extends AppCompatActivity implements GoogleApiClient.OnConnectionFailedListener, OnMapReadyCallback {

    private static final LatLngBounds BOUNDS_GREATER_SYDNEY = new LatLngBounds(
            new LatLng(-34.041458, 150.790100), new LatLng(-33.682247, 151.383362));

    protected GoogleApiClient mGoogleApiClient;
    protected GeoApiContext context;
    protected DirectionsManager directionsManager = null;

    private PlaceAutocompleteAdapter from_adapter;
    private PlaceAutocompleteAdapter to_adapter;
    private AutocompletePrediction to_prediction = null, from_prediction= null;
    private Button searchBtn, clearBtn, showGraphBtn, bikePathButton;
    private DirectionsRoute[] routes;
    private ArrayList<com.google.maps.model.LatLng> points = new ArrayList<>();
    private GoogleMap mMap;
    private AutoCompleteTextView to, from;

    private PopupWindow graphPopupWindow;
    private LayoutInflater layoutInflater;

    private int GRAPH_X_INTERVAL = 20;
    private int MAX_GRAPH_SAMPLES = 400;

    private IriaBikePath iriaBikePath = null;

    private double currentLocationLat;
    private double currentLocationLang;
    LocationResult locationResult;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // to enable getting data from Tel Aviv muni website
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        // Construct a GoogleApiClient for the {@link Places#GEO_DATA_API} using AutoManage
        // functionality, which automatically sets up the API client to handle Activity lifecycle
        // events. If your activity does not extend FragmentActivity, make sure to call connect()
        // and disconnect() explicitly.
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this, 0 /* clientId */, this)
                .addApi(Places.GEO_DATA_API)
                .build();


        setContentView(R.layout.central_activity_layout);

        from = (AutoCompleteTextView) findViewById(R.id.from);
        to = (AutoCompleteTextView) findViewById(R.id.to);
        from.setText("");
        to.setText("");

        from_adapter = new PlaceAutocompleteAdapter(this, mGoogleApiClient, BOUNDS_GREATER_SYDNEY,
                null);
        from.setAdapter(from_adapter);
        to_adapter = new PlaceAutocompleteAdapter(this, mGoogleApiClient, BOUNDS_GREATER_SYDNEY,
                null);
        to.setAdapter(to_adapter);

        final MapFragment mapFragment = (MapFragment) getFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);


        from.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                from_prediction = (AutocompletePrediction) parent.getItemAtPosition(position);
            }
        });

        to.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                to_prediction = (AutocompletePrediction) parent.getItemAtPosition(position);
            }
        });

        context = new GeoApiContext().setApiKey(getString(R.string.api_key_server));

        searchBtn = (Button) findViewById(R.id.res_button);
        searchBtn.setOnClickListener(new View.OnClickListener() {
            @Override

            public void onClick(View v) {
                if (from_prediction == null || to_prediction == null)
                    return;
                if (directionsManager != null) {
                    directionsManager.clearDirectionFromMap();
                }
                directionsManager = new DirectionsManager(context, from_prediction, to_prediction);
                directionsManager.drawAllRoutes(mMap);
                directionsManager.drawRouteMarkers(mMap);
                mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(directionsManager.getDirectionBounds(), getResources()
                        .getInteger(R.integer.bound_padding)));
            }
        });

        clearBtn = (Button) findViewById(R.id.clear_button);
        clearBtn.setOnClickListener(new View.OnClickListener() {
            @Override

            public void onClick(View v) {
                if (directionsManager != null) {
                    directionsManager.clearDirectionFromMap();
                    directionsManager = null;
                }
                to_prediction = null;
                from_prediction= null;
                from.setText("");
                to.setText("");
            }

        });

        showGraphBtn = (Button) findViewById(R.id.show_graph_button);
        showGraphBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (directionsManager == null || directionsManager.getSelectedRouteIndex() == -1) {
                    return;
                }

                LatLng ll = new LatLng(0,0);
                getCurrentLocationLatLang(ll);
                Log.i("INFO:", String.format(" current lat: %f, current lng: %f", ll.lat, ll.lng));

                getCurrentLocationLatLang(ll);
                Log.i("INFO:", String.format(" current lat: %f, current lng: %f", ll.lat, ll.lng));

                layoutInflater = (LayoutInflater) getApplicationContext().getSystemService(LAYOUT_INFLATER_SERVICE);
                ViewGroup container = (ViewGroup) layoutInflater.inflate(R.layout.graph_popup,null);

                GraphView graph = (GraphView) container.findViewById(R.id.altitude_graph);
                PathElevationGraphDrawer graphDrawer = new PathElevationGraphDrawer(graph);

                for (int i = 0; i < directionsManager.getNumRoutes(); i ++ ) {
                    PathElevationQuerier querier = new PathElevationQuerier(directionsManager.getEnodedPoylineByIndex(i));
                    long distance = directionsManager.getRouteDistanceByIndex(i);
                    int numOfSamples = querier.calcNumOfSamplesForXmetersIntervals(distance, GRAPH_X_INTERVAL, MAX_GRAPH_SAMPLES);
                    ElevationResult[] results = querier.getElevationSamples(numOfSamples);
                    graphDrawer.addSeries(results);
                    if ( results == null )
                        return;
                }

                graphDrawer.colorSeriosByIndex(directionsManager.getSelectedRouteIndex());

                DisplayMetrics dm = new DisplayMetrics();
                getWindowManager().getDefaultDisplay().getMetrics(dm);
                int width = dm.widthPixels;
                int height = dm.heightPixels;

                graphPopupWindow = new PopupWindow(container, width,300, true);
                graphPopupWindow.showAtLocation(findViewById(R.id.centralLayout), Gravity.BOTTOM, 0, 0);

                container.setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        graphPopupWindow.dismiss();
                        return false;
                    }
                });
            }
        });

        currentLocationLang = -1;
        currentLocationLat = -1;
        locationResult = new LocationResult(){
            @Override
            public void gotLocation(Location location){
//                didGetLocation = true;
                currentLocationLat = location.getLatitude();
                currentLocationLang = location.getLongitude();
                try {
                    sleep(5);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        };


        bikePathButton = (Button) findViewById(R.id.bike_button);
        bikePathButton.setOnClickListener(new View.OnClickListener(){

            @Override
            public void onClick(View v) {
                if (iriaBikePath == null){
                    try {
                        iriaBikePath = new IriaBikePath(mMap);
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (XmlPullParserException e) {
                        e.printStackTrace();
                    }
                }
                if (iriaBikePath.isBikePathShown) {
                    iriaBikePath.removeBikePathFromMap();
                }
                else {
                    iriaBikePath.showBikePathOnMap();
                }

            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Called when the Activity could not connect to Google Play services and the auto manager
     * could resolve the error automatically.
     * In this case the API is not available and notify the user.
     *
     * @param connectionResult can be inspected to determine the cause of the failure
     */
    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

        // TODO(Developer): Check error code and notify the user of error state and resolution.
        Toast.makeText(this,
                "Could not connect to Google API Client: Error " + connectionResult.getErrorCode(),
                Toast.LENGTH_SHORT).show();
    }


    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        LatLng tau = new LatLng(32.113523, 34.804399);
//        mMap.addMarker(new MarkerOptions()
//                        .title("Tel-Aviv University")
//                        .position(tau)
//        );
        mMap.moveCamera(CameraUpdateFactory.newLatLng(tau));
        mMap.moveCamera(CameraUpdateFactory.zoomTo(15f));

        mMap.setOnMapClickListener((new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng clickLatLng) {
                Log.i("inside listener begin", "inside listener begin2");
                if (directionsManager != null) {
                    MapUtils.selectClickedRoute(directionsManager, clickLatLng);
                }
            }
        }
        ));

        mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                marker.showInfoWindow();
                return true;
            }
        });


        /*double[][] arr2 =  {{32.141237, 34.800872}, {32.141489, 34.800135}, {32.141641, 34.799725}, {32.141962, 34.798795},
                {32.142071, 34.798485}, {32.142149, 34.798263}, {32.142359, 34.797588}, {32.142451, 34.797285}};
        ArrayList<com.google.android.gms.maps.model.LatLng> points = new ArrayList<>();
        for (int i = 0; i < 8; i++){
            points.add(new LatLng(arr2[i][0], arr2[i][1]));
        }
        PolylineOptions line = new PolylineOptions();
        com.google.android.gms.maps.model.LatLng currPoint;
        for (com.google.android.gms.maps.model.LatLng point : points) {
            currPoint = new com.google.android.gms.maps.model.LatLng(point.latitude, point.longitude);
            line.add(currPoint);
        }
        mMap.addPolyline(line);*/



    }


    private boolean isCurrentLocationSet(){
        MyLocation myLocation = new MyLocation();
        myLocation.getLocation(this, locationResult);
        if (currentLocationLang == -1 || currentLocationLat == -1 ) {
            return false;
        }
        return true;
    }

    private com.google.maps.model.LatLng getCurrentLocationLatLang(com.google.maps.model.LatLng latLng) {
        while ( isCurrentLocationSet() == false ) {
            try {
                sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            continue;
        }
        latLng.lat = currentLocationLat;
        latLng.lng = currentLocationLang;
        currentLocationLat = -1 ;
        currentLocationLang = -1;
        return latLng;
    }


}
