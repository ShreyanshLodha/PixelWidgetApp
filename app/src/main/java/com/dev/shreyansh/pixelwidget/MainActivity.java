package com.dev.shreyansh.pixelwidget;

import android.Manifest;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.AppBarLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.awareness.Awareness;
import com.google.android.gms.awareness.snapshot.LocationResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Scope;
import com.google.api.services.calendar.CalendarScopes;

import io.fabric.sdk.android.Fabric;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "PixelLocationWidget";
    private static final String degree = " "+(char) 0x00B0+"C";

    public Location location;
    public LocationManager locationManager;

    private final int PERMISSION_ACCESS_FINE_LOCATION = 0;
    private final int PERMISSION_ACCESS_COARSE_LOCATION = 1;

    private String currentDate;
    private SimpleDateFormat sdf;

    private JSONObject weatherData;
    private JSONObject forecastData;
    private Weather weather;

    /* UI Elements */
    TextView currentDateTV;
    TextView currentBigTemp;
    TextView currentCity;
    TextView networkStatus;
    TextView maxTemp;
    TextView minTemp;
    TextView weatherDesc;
    TextView humidity;
    TextView wind;
    TextView sunrise;
    TextView sunset;
    TextView locationDisabled;
    ImageView weatherImage;
    LinearLayout hero;
    RecyclerView recyclerView;
    ProgressDialog progressDialog;

    /* Google API Client */
    private GoogleApiClient googleApiClient;
    private final static int PLAY_SERVICES_RESOLUTION_REQUEST = 1000;

    /* Forecast data */
    private List<ForecastSingleDayWeather> forecastSingleDayWeathers;

    /* Adapter for Forecast */
    private ForecastAdapter forecastAdapter;

    Context context;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Fabric.with(this, new Crashlytics());
        setContentView(R.layout.activity_main);

        context = MainActivity.this;

        /* Bind necessary UI Elements with our code */
        bindUI();

        /* Set Day and Date */
        setDayAndDate();

        progressDialog = new ProgressDialog(this);

        /* Schedule Job if the user has logged in and if has granted us permission to access calendar */
        GoogleSignInAccount account = null;
        if (GoogleSignIn.hasPermissions(
                GoogleSignIn.getLastSignedInAccount(this), new Scope(CalendarScopes.CALENDAR)))
            account = GoogleSignIn.getLastSignedInAccount(this);
        if(account != null) {
            Util.widgetData(getApplicationContext());
        }

        /* Workaround to change the font of ActionBar */
        try {
            TextView tv = new TextView(getApplicationContext());
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(ActionBar.LayoutParams.WRAP_CONTENT, ActionBar.LayoutParams.WRAP_CONTENT);
            tv.setLayoutParams(lp);
            tv.setText(getString(R.string.app_name));
            tv.setTextSize(22);
            tv.setTextColor(Color.parseColor("#FFFFFF"));

            /* Set Custom Font */
            Typeface tf = Typeface.createFromAsset(getAssets(), "fonts/Product Sans Regular.ttf");
            tv.setTypeface(tf);
            this.getSupportActionBar().setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
            this.getSupportActionBar().setCustomView(tv);

            /* Remove shadow between Action bar and other layouts */
            this.getSupportActionBar().setElevation(0);

            /*
             * This will add shadow at the bottom of the AppBarLayout
             * Normal appBarLayout.setElevation() is not working
             * so sue the listener, this can be resource intensive
             */
            AppBarLayout appBarLayout = findViewById(R.id.appBar);
            appBarLayout.addOnOffsetChangedListener(new AppBarLayout.OnOffsetChangedListener() {
                @Override
                public void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {
                    appBarLayout.setElevation(20);
                }
            });
        } catch (NullPointerException e) {
            /*
             * If there's a null pointer exception generated,
             * then just use setTitle and
             * screw TypeFace in that case
             */
            Log.e(TAG,e.toString());
            this.getSupportActionBar().setTitle("Pixel Weather");
        }

        /*
         * Hide Hero section
         * It will load when the data is fetched from the internet
         */
        hero.setVisibility(View.INVISIBLE);
        /* Request for location access if we don't have access already */
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.ACCESS_FINE_LOCATION },
                    PERMISSION_ACCESS_FINE_LOCATION);
            ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.ACCESS_COARSE_LOCATION },
                    PERMISSION_ACCESS_COARSE_LOCATION);
        } else {
            try {
                if (checkNetwork()) {
                    if (checkPlayServices()) {
                        progressDialog.setIndeterminate(false);
                        progressDialog.setTitle("Loading...");
                        progressDialog.setMessage("Featching location and weather details...");
                        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                        progressDialog.setCancelable(false);
                        progressDialog.show();
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if(pingGoogle()) {
                                    /* Fetch Location */
                                    apiClientWork();
                                } else {
                                    progressDialog.dismiss();
                                    final AlertDialog builder = new AlertDialog.Builder(context, R.style.AlertDialogStyle)
                                            .setCancelable(false)
                                            .setTitle("Network TimeOut")
                                            .setMessage("We are unable to connect to the servers. Please check your internet.")
                                            .setNegativeButton("Exit", new DialogInterface.OnClickListener() {
                                                @Override
                                                public void onClick(DialogInterface dialog, int which) {
                                                    finish();
                                                }
                                            })
                                            .setPositiveButton("Retry", new DialogInterface.OnClickListener() {
                                                @Override
                                                public void onClick(DialogInterface dialog, int which) {
                                                    refreshUI();
                                                }
                                            })
                                            .show();
                                    builder.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(getResources().getColor(R.color.colorAccent));
                                    builder.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getResources().getColor(R.color.colorAccent));
                                }
                            }
                        },500);
                    }
                } else {
                    final AlertDialog builder = new AlertDialog.Builder(this, R.style.AlertDialogStyle)
                            .setCancelable(false)
                            .setTitle("No Internet detected.")
                            .setMessage("Enable internet to use the app.")
                            .setPositiveButton("GO TO SETTINGS", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Intent settings = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
                                    startActivityForResult(settings, 2);
                                }
                            })
                            .setNegativeButton("EXIT", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    finish();
                                }
                            }).show();
                    builder.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(getResources().getColor(R.color.colorAccent));
                    builder.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getResources().getColor(R.color.colorAccent));
                }
            } catch (Exception r) {
                Log.e(TAG, "Error",r);
            }

        }
    }

    /* Handle User's response after permission pop-up */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_ACCESS_FINE_LOCATION:
            case PERMISSION_ACCESS_COARSE_LOCATION:
                for(int a : grantResults){
                    Log.i(TAG,String.valueOf(a));
                }
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    /* Restart the app. */
                    Intent i = getBaseContext().getPackageManager()
                            .getLaunchIntentForPackage( getBaseContext().getPackageName() );
                    i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(i);
                } else {
                    /* Show alert with appropriate options */
                    AlertDialog builder = new AlertDialog.Builder(this)
                            .setMessage("We require Location permission.")
                            .setTitle("Location Permission.")
                            .setCancelable(false)
                            .setPositiveButton("Grant Permission", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Intent intent = new Intent();
                                    intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                                    intent.setData(uri);
                                    startActivity(intent);
                                }
                            })
                            .setNegativeButton("Close App", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    finish();
                                }
                            })
                            .show();
                    builder.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(getResources().getColor(R.color.colorAccent));
                    builder.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getResources().getColor(R.color.colorAccent));
                }
                break;
        }
    }

    /* Initialize listener, request and manager */
    public synchronized boolean locationUserspaceHandler() {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        boolean gps_enabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean network_enabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

        if(!(gps_enabled || network_enabled)) {
            locationDisabled.setVisibility(View.VISIBLE);
            final AlertDialog builder = new AlertDialog.Builder(this, R.style.AlertDialogStyle)
                    .setCancelable(false)
                    .setTitle("Locations are disabled")
                    .setMessage("Enable location to use the app.")
                    .setPositiveButton("GO TO SETTINGS", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Intent settings = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                            startActivityForResult(settings, 2);
                        }
                    })
                    .setNegativeButton("EXIT", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    }).show();
            builder.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(getResources().getColor(R.color.colorAccent));
            builder.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getResources().getColor(R.color.colorAccent));
            return false;

        } else {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    locationDisabled.setVisibility(View.INVISIBLE);
                }
            });

            return true;
        }
    }

    /* Update the changed location and UI with it */
    public void setLocation(Location newLocation) {
        if(checkNetwork()) {
            if (newLocation.getLongitude() != location.getLongitude() && newLocation.getLatitude() != location.getLatitude()) {
                location = newLocation;
                writeDataToUI();
            }
        } else {
            final AlertDialog builder = new AlertDialog.Builder(this, R.style.AlertDialogStyle)
                    .setCancelable(false)
                    .setTitle("No Internet detected.")
                    .setMessage("Enable internet to use the app.")
                    .setPositiveButton("GO TO SETTINGS", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Intent settings = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
                            startActivityForResult(settings, 2);
                        }
                    })
                    .setNegativeButton("EXIT", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    }).show();
            builder.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(getResources().getColor(R.color.colorAccent));
            builder.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getResources().getColor(R.color.colorAccent));
        }
    }

    /* Bind necessary elements of UI to this code */
    public void bindUI() {
        currentDateTV = findViewById(R.id.todays_date);
        currentBigTemp = findViewById(R.id.current_big_temp);
        currentCity = findViewById(R.id.city_name);
        networkStatus = findViewById(R.id.network_status);
        maxTemp = findViewById(R.id.max_temp);
        minTemp = findViewById(R.id.min_temp);
        weatherDesc = findViewById(R.id.weather_desc);
        humidity = findViewById(R.id.humidity);
        wind = findViewById(R.id.wind);
        sunrise = findViewById(R.id.sunrise);
        sunset = findViewById(R.id.sunset);
        weatherImage = findViewById(R.id.current_weather_image);
        hero = findViewById(R.id.hero_layout);
        recyclerView = findViewById(R.id.recyclerView);
        locationDisabled = findViewById(R.id.location_disabled);
    }

    /* Function to set Current Day and date in UI */
    public void setDayAndDate() {
        /* Find Current day of the week
         * Add HTML style to it */
        sdf = new SimpleDateFormat("EEEE");
        currentDate = "<b>" + sdf.format(System.currentTimeMillis()) + "</b>";
        sdf = new SimpleDateFormat("dd");
        currentDate = currentDate + ", " + sdf.format(System.currentTimeMillis());
        currentDateTV.setText(Html.fromHtml(currentDate));
    }

    /* Check if required version of play services is available in device or not */
    @SuppressWarnings("deprecation")
    private boolean checkPlayServices() {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if(resultCode != ConnectionResult.SUCCESS){
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
                GooglePlayServicesUtil.getErrorDialog(resultCode, this,
                        PLAY_SERVICES_RESOLUTION_REQUEST).show();
            } else {
                Toast.makeText(this,
                        "This device is not supported.", Toast.LENGTH_LONG)
                        .show();
                finish();
            }
            return false;
        }
        return true;
    }


    /* Write Weather Data to UI */
    public synchronized void writeDataToUI(){
        if (location != null) {
            try {
                /* Get weather data  */
                if(progressDialog.isShowing())
                    progressDialog.dismiss();
                if(hero.getVisibility()!=View.VISIBLE)
                    hero.setVisibility(View.VISIBLE);
                if(recyclerView.getVisibility()!=View.VISIBLE)
                    recyclerView.setVisibility(View.VISIBLE);
                Animation fadeIn = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.fade_in);
                hero.startAnimation(fadeIn);
                progressDialog.setTitle("Loading Data");
                progressDialog.setMessage("Fetching location and weather details.");
                weatherData = new FetchAsync().execute(location.getLatitude(), location.getLongitude()).get();
                weather = new Weather(weatherData);
                forecastData = new FetchAsyncForecast().execute(location.getLatitude(), location.getLongitude()).get();
                FetchAndProcessForecastWeather dummy = new FetchAndProcessForecastWeather();
                forecastSingleDayWeathers = dummy.processData(forecastData);
                if (forecastData != null || weatherData != null || forecastSingleDayWeathers.size()!=0 ) {

                    Log.i(TAG, String.valueOf(forecastSingleDayWeathers.size()));
                    forecastAdapter = new ForecastAdapter(forecastSingleDayWeathers, (Activity) context);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(context);
                            recyclerView.setLayoutManager(layoutManager);
                            recyclerView.setHasFixedSize(true);
                            recyclerView.setItemAnimator(new DefaultItemAnimator());
                            recyclerView.addItemDecoration(new DividerItemDecoration(context, null));
                            recyclerView.setAdapter(forecastAdapter);
                        }
                    });

                    /* Set Data in UI */
                    currentBigTemp.setText(String.valueOf(weather.getCurrentTemperature()) + degree);
                    currentCity.setText(Html.fromHtml(weather.getCityName() + ", <b>" + weather.getCountryCode() + "</b>"));
                    maxTemp.setText(String.valueOf(weather.getMaxTemperature()) + degree);
                    minTemp.setText(String.valueOf(weather.getMinTemperature()) + degree);
                    weatherDesc.setText(Html.fromHtml(" <b>" + weather.getMain() + "</b> - " + StringUtils.capitalize(weather.getDescription())));
                    humidity.setText(String.valueOf(weather.getHumidity()) + "%");
                    wind.setText(String.valueOf(weather.getWindSpeed()) + " m/s");
                    sunrise.setText(weather.getSunrise());
                    sunset.setText(weather.getSunset());
                    weatherImage.setImageResource(returnImageRes(weather.getDescription(), weather.getIsDayTime()));
                    googleApiClient.disconnect();
                } else {
                    final AlertDialog builder = new AlertDialog.Builder(this, R.style.AlertDialogStyle)
                            .setCancelable(false)
                            .setTitle("Network TimeOut")
                            .setMessage("We are unable to connect to the servers. Please check your internet.")
                            .setNegativeButton("Exit", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    finish();
                                }
                            })
                            .show();
                    builder.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(getResources().getColor(R.color.colorAccent));
                }
            }
            catch (Exception e) {
                Log.e(TAG, e.toString());
            }
        }
    }

    public int returnImageRes(String weather, boolean isStillDay) {
        if(isStillDay) {
            switch (weather.toLowerCase().trim()) {
                case "clear sky":
                case "sky is clear": return R.drawable.danieledesantis_weather_icons_sunny;
                case "few clouds":return R.drawable.danieledesantis_weather_icons_cloudy;
                case "scattered clouds": return R.drawable.danieledesantis_weather_icons_cloudy_two;
                case "broken clouds" : return R.drawable.danieledesantis_weather_icons_cloudy_three;
                case "shower rain":
                case "moderate rain": return R.drawable.danieledesantis_weather_icons_rainy_two;
                case "rain":
                case "light rain": return R.drawable.danieledesantis_weather_icons_rainy;
                case "thunderstorm":
                case "heavy intensity rain":return R.drawable.danieledesantis_weather_icons_stormy;
                case "snow": return R.drawable.danieledesantis_weather_icons_snowy;
                default: return R.drawable.danieledesantis_weather_icons_cloudy;
            }
        } else {
            switch (weather.toLowerCase().trim()) {
                case "clear sky":
                case "sky is clear": return R.drawable.danieledesantis_weather_icons_night_clear;
                case "few clouds":return R.drawable.danieledesantis_weather_icons_night_cloudy;
                case "scattered clouds": return R.drawable.danieledesantis_weather_icons_night_cloudy_two;
                case "broken clouds" : return R.drawable.danieledesantis_weather_icons_night_cloudy_three;
                case "shower rain":
                case "moderate rain": return R.drawable.danieledesantis_weather_icons_night_rainy_two;
                case "rain":
                case "light rain": return R.drawable.danieledesantis_weather_icons_night_rainy;
                case "thunderstorm":
                case "heavy intensity rain": return R.drawable.danieledesantis_weather_icons_night_stormy;
                case "snow": return R.drawable.danieledesantis_weather_icons_night_snowy;
                default: return R.drawable.danieledesantis_weather_icons_night_cloudy;
            }
        }
    }

    public boolean checkNetwork() {
        /* Check the network status */
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo netInfo = cm.getActiveNetworkInfo();
            if (!(netInfo != null && netInfo.isConnected())) {
                networkStatus.setVisibility(View.VISIBLE);
            }
            else {
                networkStatus.setVisibility(View.GONE);
            }

            //should check null because in airplane mode it will be null
            return (netInfo != null && netInfo.isConnected());
        }
        catch (Exception e) {
            Log.e(TAG, e.toString());
            return false;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == 2) {
            Intent i = getBaseContext().getPackageManager()
                    .getLaunchIntentForPackage( getBaseContext().getPackageName() );

            if(progressDialog.isShowing())
                progressDialog.dismiss();

            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.action_bar_main_activity, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.google_sign_in:
                final Intent googleActivity = new Intent(this, GoogleAccountsActivity.class);
                startActivity(googleActivity);
                overridePendingTransition(R.anim.enter, R.anim.exit);
                return true;
            case R.id.exit:
                finish();
                return true;
            case R.id.refresh:
                refreshUI();
                return true;
            case R.id.settings:
                Intent goToSettings = new Intent(this, SettingsActivity.class);
                startActivity(goToSettings);
                overridePendingTransition(R.anim.enter, R.anim.exit);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private boolean pingGoogle() {
        try {
            String command = "ping -c 1 google.com";
            return (Runtime.getRuntime().exec(command).waitFor() == 0);
        } catch (Exception e) {
            Log.i(TAG, "Error", e);
            return false;
        }
    }

    private void refreshUI() {
        hero.setVisibility(View.INVISIBLE);
        progressDialog.setTitle("Refreshing");
        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progressDialog.setMessage("Fetching location and data.");
        progressDialog.setCancelable(false);
        progressDialog.setIndeterminate(true);
        progressDialog.show();

        new Thread() {
            public synchronized void run() {
                Looper.prepare();
                if(pingGoogle()) {
                    /* Fetch Location */
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Log.e(TAG, e.toString());
                    }
                    if(locationUserspaceHandler()) {
                        apiClientWork();
                    }

                } else {
                    progressDialog.dismiss();
                    final AlertDialog builder = new AlertDialog.Builder(context, R.style.AlertDialogStyle)
                            .setCancelable(false)
                            .setTitle("Network TimeOut")
                            .setMessage("We are unable to connect to the servers. Please check your internet.")
                            .setPositiveButton("Retry", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    refreshUI();
                                }
                            })
                            .setNegativeButton("Exit", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    finish();
                                }
                            })
                            .show();
                    builder.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(getResources().getColor(R.color.colorAccent));
                    builder.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getResources().getColor(R.color.colorAccent));
                }
            }
        }.start();
    }

    private synchronized void apiClientWork() {
        if(checkPlayServices()) {
            googleApiClient = new GoogleApiClient.Builder(context)
                    .addApi(Awareness.API)
                    .addConnectionCallbacks(connectionCallbacks())
                    .build();
            googleApiClient.connect();
        }
    }

    @SuppressWarnings("deprecation")
    private GoogleApiClient.ConnectionCallbacks connectionCallbacks() {
        return new GoogleApiClient.ConnectionCallbacks() {
            @Override
            public void onConnected(@Nullable Bundle bundle) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
                    return;
                } else {
                    Awareness.SnapshotApi.getLocation(googleApiClient).setResultCallback(new ResultCallback<LocationResult>() {
                        @Override
                        public void onResult(@NonNull LocationResult locationResult) {
                            if (locationResult.getStatus().isSuccess()) {
                                location = locationResult.getLocation();
                                Log.i(TAG, location.getLatitude() + "");
                                writeDataToUI();
                            }
                        }
                    });
                }
            }

            @Override
            public void onConnectionSuspended(int i) {

            }
        };
    }
}
