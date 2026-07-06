package com.runstate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import com.google.gson.JsonParser;

/*
 * WeatherService owns everything about talking to Open-Meteo: turning a city
 * into coordinates, asking for that day's weather, and handing back a WeatherData.
 * It exists so RunAgent no longer has to — one class, one job (single responsibility).
 */
public class WeatherService {

    // One reusable client for the whole app, with a connect timeout so a slow
    // or unreachable server can never hang the console waiting to connect.
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    // The single "we got nothing" result. Returned on any failure so callers
    // never deal with nulls-of-nulls or exceptions — always a real WeatherData.
    private static final WeatherData UNAVAILABLE = new WeatherData(null, null, null);

    // The one public method: given where and when, return that day's weather.
    // Weather is optional context, so ANY problem yields UNAVAILABLE, never a crash.
    public static WeatherData fetch(String city, String state, LocalDate date) {
        try {
            if (city == null || city.isBlank()) {
                return UNAVAILABLE;
            }
            double[] coords = geocode(city, state);
            if (coords == null) {
                return UNAVAILABLE;
            }
            return fetchForecast(coords[0], coords[1], date);
        } catch (Exception e) {
            return UNAVAILABLE;
        }
    }

    // Turns a city name into [latitude, longitude]. Asks for up to 5 candidates
    // and, when we know the runner's state, picks the one whose admin1 (the API's
    // word for state/region) matches — so "Springfield" resolves to the right one.
    private static double[] geocode(String city, String state) throws Exception {
        String url = "https://geocoding-api.open-meteo.com/v1/search?name="
                + city.replace(" ", "%20") + "&count=5";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        var results = JsonParser.parseString(response.body())
                .getAsJsonObject()
                .getAsJsonArray("results");
        if (results == null || results.size() == 0) {
            return null;
        }
        // Fall back to the first (best-ranked) result unless a state match beats it.
        var chosen = results.get(0).getAsJsonObject();
        if (state != null && !state.isBlank()) {
            for (int i = 0; i < results.size(); i++) {
                var candidate = results.get(i).getAsJsonObject();
                if (candidate.has("admin1")
                        && candidate.get("admin1").getAsString().equalsIgnoreCase(state)) {
                    chosen = candidate;
                    break;
                }
            }
        }

        double lat = chosen.get("latitude").getAsDouble();
        double lon = chosen.get("longitude").getAsDouble();
        return new double[]{lat, lon};
    }



    // Asks the FORECAST API (not the archive) for one day's weather. The forecast
    // endpoint covers today plus ~92 days back, so a run logged today has data.
    private static WeatherData fetchForecast(double lat, double lon, LocalDate date) throws Exception {
        String url = "https://api.open-meteo.com/v1/forecast?"
                + "latitude=" + lat
                + "&longitude=" + lon
                + "&start_date=" + date
                + "&end_date=" + date
                + "&daily=weather_code,temperature_2m_mean,apparent_temperature_mean"
                + "&temperature_unit=fahrenheit"
                + "&timezone=auto";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        var daily = JsonParser.parseString(response.body())
                .getAsJsonObject()
                .getAsJsonObject("daily");

        Double temperature = daily.getAsJsonArray("temperature_2m_mean").get(0).getAsDouble();
        Double apparentTemperature = daily.getAsJsonArray("apparent_temperature_mean").get(0).getAsDouble();
        int code = daily.getAsJsonArray("weather_code").get(0).getAsInt();

        return new WeatherData(temperature, apparentTemperature, decodeWeatherCode(code));
    }



    // Converts a WMO weather code returned by Open-Meteo into a readable description.
    private static String decodeWeatherCode(int code) {
        if (code == 0) return "Clear sky";
        if (code == 1) return "Mainly clear";
        if (code == 2) return "Partly cloudy";
        if (code == 3) return "Overcast";
        if (code == 45 || code == 48) return "Foggy";
        if (code >= 51 && code <= 55) return "Drizzle";
        if (code >= 61 && code <= 65) return "Rain";
        if (code >= 71 && code <= 75) return "Snow";
        if (code >= 80 && code <= 82) return "Rain showers";
        if (code == 95) return "Thunderstorm";
        if (code == 96 || code == 99) return "Thunderstorm with hail";
        return "Unknown";
    }
}
