package com.example.uberapp;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;


import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.JointType;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.SquareCap;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.suke.widget.SwitchButton;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback ,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        com.google.android.gms.location.LocationListener,
        LocationListener
{

    private GoogleMap map;

    //Play Services
    private static final int MY_PERMISSION_REQUEST_CODE = 7000;
    private static final int PLAY_SERVICE_RES_REQUEST = 7001;
    private LocationRequest locationRequest;
    private GoogleApiClient googleApiClient;
    private Location lastlocation;
    private static int UPDATE_INTERVAL = 5000;
    private static int FASTEST_INTERVAL = 3000;
    private static int DISPLACEMENT = 10;
    DatabaseReference drivers;
    GeoFire geoFire;
    LatLng latLng;
    LocationManager locationManager;
    int radius = 1; //1km
    boolean isDriverFound = false;
    String driverId = "";
    DatabaseReference geoFireRef;

    Marker markerCurrent;
    SupportMapFragment mapFragment;
    com.suke.widget.SwitchButton switchButton;

    //car animation
    private List<LatLng> polyLineList;
    private  Marker carMarker;
    private float v;
    private double lat , lng;
    private Handler handler;
    private LatLng startPostion ,  endPosition , currentPostion;
    private int index , next;
    private Button btnGo;
    private EditText edtPlace;
    private String destination;
    private PolylineOptions polylineOptions , blackPolylineOptions;
    private Polyline blackPolyline , greyPolyline;
    private View mapView;

    private IGoogleAPI mService;

    Runnable drawPathRunnable = new Runnable() {
        @Override
        public void run() {
            if(index < polyLineList.size()-1)
            {
                index++;
                next = index+1;
            }
            if(index < polyLineList.size()-1)
            {
                startPostion = polyLineList.get(index);
                endPosition = polyLineList.get(next);
            }
            ValueAnimator valueAnimator = ValueAnimator.ofFloat(0 , 1);
            valueAnimator.setDuration(3000);
            valueAnimator.setInterpolator(new LinearInterpolator());
            valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    v = valueAnimator.getAnimatedFraction();
                    lng = v*endPosition.longitude+(1-v)*startPostion.longitude;
                    lat = v*endPosition.latitude+(1-v)*startPostion.latitude;
                    LatLng newPos = new LatLng(lat , lng);
                    carMarker.setPosition(newPos);
                    carMarker.setAnchor(0.5f , 0.5f);
                    carMarker.setRotation(getBearing(startPostion , newPos));
                    map.moveCamera(CameraUpdateFactory.newCameraPosition(
                            new CameraPosition.Builder()
                            .target(newPos)
                            .zoom(15.5f)
                            .build()
                    ));
                }
            });
            valueAnimator.start();
            handler.postDelayed(this , 3000);
        }
    };

    private float getBearing(LatLng startPostion, LatLng newPos) {
        double lat = Math.abs(startPostion.latitude - endPosition.latitude);
        double lng = Math.abs(startPostion.longitude - endPosition.longitude);

        if(startPostion.latitude < endPosition.latitude && startPostion.longitude < endPosition.longitude)
            return (float) (Math.toDegrees(Math.atan(lng/lat)));

       else if(startPostion.latitude >= endPosition.latitude && startPostion.longitude < endPosition.longitude)
            return (float) ((90-Math.toDegrees(Math.atan(lng/lat)))+90);

       else if(startPostion.latitude >= endPosition.latitude && startPostion.longitude >= endPosition.longitude)
            return (float) (Math.toDegrees(Math.atan(lng/lat))+180);

       else if(startPostion.latitude < endPosition.latitude && startPostion.longitude >= endPosition.longitude)
            return (float) ((90-Math.toDegrees(Math.atan(lng/lat)))+270);
       return -1;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
         mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        //checkLocation();



        //Init View
         switchButton = findViewById(R.id.switch_button);
        switchButton.setOnCheckedChangeListener(new SwitchButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(SwitchButton view, boolean isOnline) {
                //TODO do your job

                if(isOnline)
                {

                    startlocationUpdates();
                    displayLocation();

                }
                else {
                    stopLoationUpdates();
                  // markerCurrent.remove();
                   map.clear();
                  // handler.removeCallbacks(drawPathRunnable);
                    Toast.makeText(MapsActivity.this, "You are offline ", Toast.LENGTH_SHORT).show();
                }
            }
        });


        polyLineList = new ArrayList<>();
        btnGo = findViewById(R.id.btnGo);
        edtPlace = findViewById(R.id.edtPlace);

        btnGo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                destination = edtPlace.getText().toString();
                destination = destination.replace(" ","+" ); //Replace space with + for fetch data
                getDirection();
            }
        });

            //GeoFire
        drivers = FirebaseDatabase.getInstance().getReference(Common.driver_tbl);
        geoFire = new GeoFire(drivers);
        setUpLocation();

        mService = Common.getGoogleAPI();

    }

     public void statusCheck() {
            final LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

            if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                buildAlertMessageNoGps();

            }
        }


    private void buildAlertMessageNoGps() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Your GPS seems to be disabled, do you want to enable it?")
                .setCancelable(false)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int id) {
                        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                       Toast.makeText(MapsActivity.this, "You are online", Toast.LENGTH_SHORT).show();

                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, final int id) {
                        dialog.cancel();
                    }
                });
        final AlertDialog alert = builder.create();
        alert.show();
    }


    private void getDirection() {
        currentPostion = new LatLng(lastlocation.getLatitude() , lastlocation.getLongitude());

        String requestApi = null;
        try {
            requestApi = "https://maps.googleapis.com/maps/api/directions/json?"+
                    "mode=driving&"+
                    "transit_routing_preference=less_driving&"+
                    "origin="+currentPostion.latitude+","+currentPostion.longitude+"&"+
                    "destination="+destination+"&"+
                    "key="+getResources().getString(R.string.google_direction_api);
            Log.d("ERROR" , requestApi);
            mService.getPath(requestApi)
                    .enqueue(new Callback<String>() {
                        @Override
                        public void onResponse(Call<String> call, Response<String> response) {

                            try {
                                JSONObject jsonObject = new JSONObject(response.body().toString());
                                JSONArray jsonArray = jsonObject.getJSONArray("routes");
                                for (int i = 0 ; i < jsonArray.length() ; i++)
                                {
                                    JSONObject route = jsonArray.getJSONObject(i);
                                    JSONObject poly = route.getJSONObject("overview_polyLine");
                                    String polyline = poly.getString("points");
                                    polyLineList = decodePoly(polyline);
                                }

                              /*  //Adjusting bounds
                                LatLngBounds.Builder builder = new LatLngBounds.Builder();
                                for(LatLng latLng:polyLineList)
                                    builder.include(latLng);
                                LatLngBounds bounds = builder.build();
                                CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds , 2);
                                map.animateCamera(cameraUpdate);*/

                                // checking if polylineList is Empty or not
                                if (!polyLineList.isEmpty()) {
                                    // Adjusting Bounds
                                    LatLngBounds.Builder builder = new LatLngBounds.Builder();
                                    for (LatLng latLng : polyLineList) {
                                        builder.include(latLng);
                                    }
                                    LatLngBounds bounds = builder.build();
                                    CameraUpdate mCameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, 2);
                                    map.animateCamera(mCameraUpdate);
                                }

                                polylineOptions = new PolylineOptions();
                                polylineOptions.color(Color.GRAY);
                                polylineOptions.width(5);
                                polylineOptions.startCap(new SquareCap());
                                polylineOptions.endCap(new SquareCap());
                                polylineOptions.jointType(JointType.ROUND);
                                polylineOptions.addAll(polyLineList);
                                greyPolyline = map.addPolyline(polylineOptions);

                                blackPolylineOptions = new PolylineOptions();
                                blackPolylineOptions.color(Color.BLACK);
                                blackPolylineOptions.width(5);
                                blackPolylineOptions.startCap(new SquareCap());
                                blackPolylineOptions.endCap(new SquareCap());
                                blackPolylineOptions.jointType(JointType.ROUND);
                                blackPolyline = map.addPolyline(blackPolylineOptions);

                                map.addMarker(new MarkerOptions().position(polyLineList.get(polyLineList.size()-1))
                                .title("Pickup Location"));

                                //Animation
                                ValueAnimator polyLineAnimator = ValueAnimator.ofInt(0 , 100);
                                polyLineAnimator.setDuration(2000);
                                polyLineAnimator.setInterpolator(new LinearInterpolator());
                                polyLineAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                                    @Override
                                    public void onAnimationUpdate(ValueAnimator valueAnimator) {
                                        List<LatLng> points = greyPolyline.getPoints();
                                        int percentValue = (int) valueAnimator.getAnimatedValue();
                                        int size = points.size();
                                        int newPoints = (int) (size * (percentValue/100.0f));
                                        List<LatLng> p = points.subList(0 , newPoints);
                                        blackPolyline.setPoints(p);
                                    }
                                });
                                polyLineAnimator.start();

                                carMarker = map.addMarker(new MarkerOptions().position(currentPostion)
                                .flat(true)
                                .icon(BitmapDescriptorFactory.fromResource(R.drawable.car)));

                                handler = new Handler();
                                index = -1;
                                next = 1;
                                handler.postDelayed(drawPathRunnable , 3000);

                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onFailure(Call<String> call, Throwable t) {
                            Toast.makeText(MapsActivity.this, ""+t.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });

        }catch (Exception e)
        {
            e.printStackTrace();
        }

    }

    private List<LatLng> decodePoly(String encoded) {

        List poly = new ArrayList();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            LatLng p = new LatLng((((double) lat / 1E5)),
                    (((double) lng / 1E5)));
            poly.add(p);
        }

        return poly;
    }




    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode)
        {
            case MY_PERMISSION_REQUEST_CODE:
                if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    if(checkPlayServices())
                    {
                        buildGoogleApiClient();
                        creatLocationReaquest();
                        if(switchButton.isChecked())
                        {

                            displayLocation();
                        }
                    }

                }


        }

    }

    private void setUpLocation() {

        if(ActivityCompat.checkSelfPermission(this , Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this , Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            //Request Runtime permission
            ActivityCompat.requestPermissions(this , new String[]{
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
            } , MY_PERMISSION_REQUEST_CODE);
        }
        else {
            if(checkPlayServices())
            {
                buildGoogleApiClient();
                creatLocationReaquest();
                if(switchButton.isChecked())
                {
                    displayLocation();
                }
            }
        }
    }

    private void creatLocationReaquest() {
        locationRequest = new LocationRequest();
        locationRequest.setInterval(UPDATE_INTERVAL);
        locationRequest.setFastestInterval(FASTEST_INTERVAL);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setSmallestDisplacement(DISPLACEMENT);
    }

    private void buildGoogleApiClient() {
        googleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        googleApiClient.connect();
    }

    private boolean checkPlayServices() {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode))
                GooglePlayServicesUtil.getErrorDialog(resultCode, this, PLAY_SERVICE_RES_REQUEST).show();
            else {
                Toast.makeText(this, "This is not supported", Toast.LENGTH_SHORT).show();
                finish();
            }
            return false;
        }
        return  true;
    }

    private void stopLoationUpdates() {

        if(ActivityCompat.checkSelfPermission(this , Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this , Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            return;
        }
        LocationServices.FusedLocationApi.removeLocationUpdates(googleApiClient ,  this);
    }

    private void displayLocation() {
        if(ActivityCompat.checkSelfPermission(this , Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this , Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            return;
        }
        lastlocation = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);
        if(lastlocation != null)
        {
            if(switchButton.isChecked())
            {
                final double latitude = lastlocation.getLatitude();
                final double longitude = lastlocation.getLongitude();


                //Update to firebase
                geoFire.setLocation(FirebaseAuth.getInstance().getCurrentUser().getUid(), new GeoLocation(latitude, longitude), new GeoFire.CompletionListener() {
                    @Override
                    public void onComplete(String key, DatabaseError error) {
                        //Add Marker
                        if (markerCurrent != null)
                        markerCurrent.remove(); //Remove already marker
                        markerCurrent = map.addMarker(new MarkerOptions()
                                        //.title(String.valueOf(BitmapDescriptorFactory.fromResource(R.drawable.car)))
                                        .position(new LatLng(latitude , longitude))
                                        .title("Your Location"));

                        //Move marker to this position
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(latitude , longitude) , 15.0f));


                    }
                });


            }
        }
        else {
            Log.d("ERROR" , "Cannot get your location");
        }
    }

    private void rotateMarker(final Marker markerCurrent, final Float i, GoogleMap map) {
        final Handler handler = new Handler();
        final long start = SystemClock.uptimeMillis();
        final float startRotation = markerCurrent.getRotation();
        final long duration = 1500;

        final Interpolator interpolator = new LinearInterpolator();
        handler.post(new Runnable() {
            @Override
            public void run() {
                long elapsed = SystemClock.uptimeMillis() - start;
                float t = interpolator.getInterpolation((float)elapsed/duration);
                float rot = t*i+(1-t)*startRotation;
                markerCurrent.setRotation(-rot > 180?rot/2:rot);

                if(t<1.0)
                {
                    handler.postDelayed(this , 16);

                }
            }
        });


    }

    private void startlocationUpdates() {
       if(ActivityCompat.checkSelfPermission(this , Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this , Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient , locationRequest ,  this);

    }



    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;
        map.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        map.setTrafficEnabled(false);
        map.setIndoorEnabled(false);
        map.setBuildingsEnabled(false);
        map.getUiSettings().setZoomControlsEnabled(true);
        //googleMap.setMyLocationEnabled(true); // Location btn is enabled by setMyLocationEnabled
        statusCheck();


    }


    @Override
    public void onLocationChanged(Location location) {
            lastlocation = location;
            displayLocation();
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

    @Override
    public void onConnected(@Nullable Bundle bundle) {
            displayLocation();
            startlocationUpdates();
    }

    @Override
    public void onConnectionSuspended(int i) {
        googleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }
}
