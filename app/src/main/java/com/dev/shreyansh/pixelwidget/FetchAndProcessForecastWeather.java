package com.dev.shreyansh.pixelwidget;

import android.util.Log;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * Created by shreyansh on 3/4/18.
 */

public class FetchAndProcessForecastWeather {
    private final static String TAG = "F&P-ForecastWeather";

    private List<ForecastSingleDayWeather> forecastSingleDayWeathers;

    private String query = "http://api.openweathermap.org/data/2.5/forecast?lat=%s&lon=%s&units=metric&cnt=8&appid=%s";
    private double latitude;
    private double longitude;

    private HttpClient client;
    private HttpGet httpGet;
    private HttpResponse httpResponse;

    /* Default constructor which should receive latitude and longitude */
    public FetchAndProcessForecastWeather(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
        query = String.format(query, latitude, longitude, OpenWeatherKey.KEY);
        Log.i(TAG, query);
    }

    /* Fetch data in AsyncTask */
    public JSONObject fetchData() {
        client = new DefaultHttpClient();
        httpGet = new HttpGet(query);

        try {
            httpResponse = client.execute(httpGet);
            HttpEntity entity = httpResponse.getEntity();
            return new JSONObject(EntityUtils.toString(entity));
        } catch (Exception e) {
            Log.i(TAG, e.toString());
            return null;
        }
    }

    /* Return the forecast in a list */
    public List<ForecastSingleDayWeather> processData(JSONObject data) {
        forecastSingleDayWeathers = null;
        try {
            /*
             * If location exists the return will be 200
             * Then only proceed further for fetching data
             */
            if (data.getInt("cod") == 200) {
                /* All forecast are in a JSONArray
                 * of KEY called 'list' */
                JSONArray allForecast = data.getJSONArray("list");
                JSONObject singleData;
                JSONObject subObject;

                /* Should be 8 */
                int len = allForecast.length();
                /* Take Forecast for next 7 days */
                for (int i = 1; i < len; i++) {
                    /* Process Forecast of one day at a time */
                    singleData = allForecast.getJSONObject(i);

                    /*
                     * Initialize forecast class empty
                     * and later use setter functions to initialize the value
                     */
                    ForecastSingleDayWeather singleDayWeather = new ForecastSingleDayWeather();

                    /*
                     * Process JSONObject and set all the attributes of
                     * Forecast class as per the processed JSONObject
                     */
                    singleDayWeather.setDateText(singleData.getString("dt_txt"));
                    subObject = singleData.getJSONObject("main");
                    singleDayWeather.setTemperature(subObject.getDouble("temp"));
                    singleDayWeather.setMinTemperature(subObject.getDouble("temp_min"));
                    singleDayWeather.setMaxTemperature(subObject.getDouble("temp_max"));
                    singleDayWeather.setHumidity(subObject.getDouble("humidity"));
                    subObject = singleData.getJSONObject("wind");
                    singleDayWeather.setWindspeed(subObject.getDouble("speed"));
                    subObject = new JSONArray(singleData.getJSONArray("weather")).getJSONObject(0);
                    singleDayWeather.setMainWeather(subObject.getString("main"));
                    singleDayWeather.setDescWeather(subObject.getString("description"));

                    /* add each day's fetched data to List of forecast class */
                    forecastSingleDayWeathers.add(singleDayWeather);
                }
            } else {
                /* No data is there to be processed */
                Log.i(TAG, "Location Data not available");
            }
        } catch (Exception e) {
            Log.i(TAG, e.toString());
        }

        /* This will be either null or it will have all the date */
        return forecastSingleDayWeathers;
    }
}