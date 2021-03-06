package org.neshan.sample.starter.activity;

// common project libraries

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import org.neshan.sample.R;
import org.neshan.sample.starter.util.RecordKeeper;
import org.neshan.core.LngLat;
import org.neshan.core.LngLatVector;
import org.neshan.geometry.LineGeom;
import org.neshan.geometry.PolygonGeom;
import org.neshan.graphics.ARGB;
import org.neshan.layers.VectorElementLayer;
import org.neshan.services.NeshanMapStyle;
import org.neshan.services.NeshanServices;
import org.neshan.styles.AnimationStyle;
import org.neshan.styles.AnimationStyleBuilder;
import org.neshan.styles.AnimationType;
import org.neshan.styles.LabelStyle;
import org.neshan.styles.LabelStyleCreator;
import org.neshan.styles.LineStyle;
import org.neshan.styles.LineStyleCreator;
import org.neshan.styles.MarkerStyle;
import org.neshan.styles.MarkerStyleCreator;
import org.neshan.styles.PolygonStyle;
import org.neshan.styles.PolygonStyleCreator;
import org.neshan.ui.ClickData;
import org.neshan.ui.ClickType;
import org.neshan.ui.MapEventListener;
import org.neshan.ui.MapView;
import org.neshan.vectorelements.Label;
import org.neshan.vectorelements.Line;
import org.neshan.vectorelements.Marker;
import org.neshan.vectorelements.Polygon;

// libraries to get user location
// libraries to show Neshan map on screen
// libraries to save user location and all other locations used in apk

public class MainActivity extends AppCompatActivity implements  NavigationView.OnNavigationItemSelectedListener  {

    private static final String TAG = MainActivity.class.getName();

    // Time between location updates (500 milliseconds or 0.5 seconds)
    final long MIN_TIME = 500;
    // Distance between location updates (1m)
    final float MIN_DISTANCE = 1;

    // used to track request permissions
    final int REQUEST_CODE = 123;

    final int BASE_MAP_INDEX = 0;

    // Network provider
    String LOCATION_PROVIDER = LocationManager.NETWORK_PROVIDER;
    LocationManager locationManager;
    LocationListener locationListener;

    // User's current location
    Location userLocation;
    LngLat clickedLocation;

    // Neshan Map
    MapView map;
    NeshanMapStyle mapStyle;
    DrawerLayout drawer;
    NavigationView nav;
    Toolbar toolbar;
    FloatingActionButton focusOnUserLocationBtn;
    Button drawLineBtn;
    Button drawPolylineBtn;

    VectorElementLayer userMarkerLayer;
    VectorElementLayer markerLayer;
    VectorElementLayer lineLayer;
    VectorElementLayer polygonLayer;
    VectorElementLayer labelLayer;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initLayoutReferences();
    }

    private void initLayoutReferences() {
        initViews();
        initToolbar();
        initMap();
        initSideNavigation();

        //on side navigation menu clicked
        nav.setNavigationItemSelectedListener(this);

        // when focusOnUserLocation is clicked, camera points to user location
        focusOnUserLocationBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (userLocation != null) {
                    addUserMarker(new LngLat(userLocation.getLongitude(), userLocation.getLatitude()));

                    map.setFocalPointPosition(
                            new LngLat(userLocation.getLongitude(), userLocation.getLatitude()),
                            0.25f);
                    map.setZoom(15, 0.25f);
                }
            }
        });

        // when clicked on map, a marker is added in click location
        map.setMapEventListener(new MapEventListener(){
            @Override
            public void onMapClicked(ClickData mapClickInfo){
                if(mapClickInfo.getClickType() == ClickType.CLICK_TYPE_LONG) {
                    clickedLocation = mapClickInfo.getClickPos();
                    LngLat lngLat = new LngLat(clickedLocation.getX(), clickedLocation.getY());
                    addMarker(lngLat);
                }
            }
        });

        drawLineBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                drawLineGeom();
            }
        });

        drawPolylineBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                drawPolygonGeom();
            }
        });
    }

    private void initSideNavigation() {
        Menu menu = nav.getMenu();
        switch (mapStyle) {
            case NESHAN:
                menu.findItem(R.id.side_nav_theme_neshan).setChecked(true);
                break;
            case STANDARD_NIGHT:
                menu.findItem(R.id.side_nav_theme_standard_night).setChecked(true);
                break;
            case STANDARD_DAY:
                menu.findItem(R.id.side_nav_theme_standard_day).setChecked(true);
                break;
        }
    }

    private void initViews() {
        map = findViewById(R.id.map);
        drawer = findViewById(R.id.drawer_layout);
        nav = findViewById(R.id.side_nav);
        toolbar = findViewById(R.id.toolbar);
        focusOnUserLocationBtn = findViewById(R.id.user_location_btn);
        drawLineBtn = findViewById(R.id.draw_line);
        drawPolylineBtn = findViewById(R.id.draw_polyline_btn);
    }

    private void initToolbar() {
        Drawable navIcon = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_dehaze, null);
        toolbar.setNavigationIcon(navIcon);
        setSupportActionBar(toolbar);
    }

    private void initMap(){
        userMarkerLayer = NeshanServices.createVectorElementLayer();
        markerLayer = NeshanServices.createVectorElementLayer();
        lineLayer = NeshanServices.createVectorElementLayer();
        polygonLayer = NeshanServices.createVectorElementLayer();
        labelLayer = NeshanServices.createVectorElementLayer();
        map.getLayers().add(userMarkerLayer);
        map.getLayers().add(markerLayer);
        map.getLayers().add(lineLayer);
        map.getLayers().add(polygonLayer);
        map.getLayers().add(labelLayer);

        updateCurrentLocation();

        // add Standard_day map to layers
        mapStyle = RecordKeeper.instance(this).getMapTheme();
        map.getLayers().insert(BASE_MAP_INDEX, NeshanServices.createBaseMap(mapStyle));
    }

    public void updateCurrentLocation(){
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                // when location of user changes, a marker will be added to that location
                userLocation = location;

                addUserMarker(new LngLat(userLocation.getLongitude(), userLocation.getLatitude()));

                map.setFocalPointPosition(
                        new LngLat(userLocation.getLongitude(), userLocation.getLatitude()),
                        0.25f);
                map.setZoom(15,0.25f);
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {

            }

            @Override
            public void onProviderEnabled(String provider) {

            }

            @Override
            public void onProviderDisabled(String provider) {

            }
        };

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_CODE);
            return;
        }
        locationManager.requestLocationUpdates(LOCATION_PROVIDER, MIN_TIME, MIN_DISTANCE, locationListener);

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case android.R.id.home:
                drawer.openDrawer(GravityCompat.START);
                return true;
            default:
                return false;
        }
    }
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        Intent intent = new Intent(MainActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        switch (id){
            case R.id.side_nav_theme_neshan:
                drawer.closeDrawer(GravityCompat.START, false);
                RecordKeeper.instance(this).setMapTheme(NeshanMapStyle.NESHAN);
                break;
            case R.id.side_nav_theme_standard_night:
                drawer.closeDrawer(GravityCompat.START, false);
                RecordKeeper.instance(this).setMapTheme(NeshanMapStyle.STANDARD_NIGHT);
                break;
            case R.id.side_nav_theme_standard_day:
                drawer.closeDrawer(GravityCompat.START, false);
                RecordKeeper.instance(this).setMapTheme(NeshanMapStyle.STANDARD_DAY);
                break;
        }
        startActivity(intent);
        overridePendingTransition(R.anim.theme_switch_fade_in, R.anim.theme_switch_fade_out);
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if(requestCode == REQUEST_CODE){
            if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                updateCurrentLocation();
            }
            else{
                Log.d(TAG, "Permission Denied :(");
            }

        }
    }

    private void addMarker(LngLat loc){
        markerLayer.clear();
        labelLayer.clear();

        AnimationStyleBuilder animStBl = new AnimationStyleBuilder();
        animStBl.setFadeAnimationType(AnimationType.ANIMATION_TYPE_SMOOTHSTEP);
        animStBl.setSizeAnimationType(AnimationType.ANIMATION_TYPE_SPRING);
        animStBl.setPhaseInDuration(0.5f);
        animStBl.setPhaseOutDuration(0.5f);
        AnimationStyle animSt = animStBl.buildStyle();

        MarkerStyleCreator markStCr = new MarkerStyleCreator();
        markStCr.setSize(18f);
        markStCr.setAnimationStyle(animSt);
        MarkerStyle markSt = markStCr.buildStyle();

        Marker marker = new Marker(loc, markSt);

        LabelStyleCreator labStCr = new LabelStyleCreator();
        labStCr.setBackgroundColor(new ARGB((short) 100, (short) 100, (short) 100, (short) 100));
        labStCr.setStrokeWidth(20f);
        LabelStyle labSt = labStCr.buildStyle();
        Label label = new Label(loc, labSt, loc.getY() + "," + loc.getX());
        Log.d("markerLayer", "lat= " + loc.getY() + " lon= " + loc.getX());


        markerLayer.add(marker);
        labelLayer.add(label);
    }

    private void addUserMarker(LngLat loc){
        userMarkerLayer.clear();

        AnimationStyleBuilder animStBl = new AnimationStyleBuilder();
        animStBl.setFadeAnimationType(AnimationType.ANIMATION_TYPE_SMOOTHSTEP);
        animStBl.setSizeAnimationType(AnimationType.ANIMATION_TYPE_SPRING);
        animStBl.setPhaseInDuration(0.5f);
        animStBl.setPhaseOutDuration(0.5f);
        AnimationStyle animSt = animStBl.buildStyle();

        MarkerStyleCreator markStCr = new MarkerStyleCreator();
        markStCr.setSize(18f);
        markStCr.setAnimationStyle(animSt);
        MarkerStyle markSt = markStCr.buildStyle();

        Marker marker = new Marker(loc, markSt);

        userMarkerLayer.add(marker);
    }

    private LineGeom drawLineGeom(){
        LngLatVector lngLatVector = new LngLatVector();
        lngLatVector.add(new LngLat(59.540182, 36.314163));
        lngLatVector.add(new LngLat(59.539290, 36.310654));
        LineGeom lineGeom = new LineGeom(lngLatVector);
        Line line = new Line(lineGeom, getLineStyle());
        lineLayer.clear();
        lineLayer.add(line);
        return lineGeom;
    }

    private LineStyle getLineStyle(){
        LineStyleCreator lineStCr = new LineStyleCreator();
        lineStCr.setColor(new ARGB((short) 2, (short) 119, (short) 189, (short)190));
        lineStCr.setWidth(12f);
        lineStCr.setStretchFactor(0f);
        return lineStCr.buildStyle();
    }

    private PolygonGeom drawPolygonGeom(){
        LngLatVector lngLatVector = new LngLatVector();
        lngLatVector.add(new LngLat(59.55231, 36.30745));
        lngLatVector.add(new LngLat(59.54819, 36.31044));
        lngLatVector.add(new LngLat(59.55079, 36.31296));
        lngLatVector.add(new LngLat(59.55504, 36.31016));
        PolygonGeom polygonGeom = new PolygonGeom(lngLatVector);
        Polygon polygon = new Polygon(polygonGeom, getPolygonStyle());
        polygonLayer.clear();
        polygonLayer.add(polygon);
        return polygonGeom;
    }

    private PolygonStyle getPolygonStyle(){
        PolygonStyleCreator polygonStCr = new PolygonStyleCreator();
        polygonStCr.setLineStyle(getLineStyle());
        return polygonStCr.buildStyle();
    }
}