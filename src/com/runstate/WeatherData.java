package com.runstate;

/*
 * WeatherData is a value object: a small, immutable class whose whole job
 * is to hold a few related pieces of data as one unit.
 *
 * Before this class, a Run carried weather as two loose fields
 * (a double temperature and a String condition). Bundling them into one
 * object means a Run can hold a single "weather" thing instead of several
 * separate weather variables — this is composition, which we'll use in Run.
 *
 * "Immutable" means once a WeatherData is built, its values never change.
 * There are no setters, and every field is final. That models reality:
 * the weather during a run is a fact of that moment, not something we edit later.
 */
public class WeatherData {

    // Air temperature in Fahrenheit, or null if weather could not be fetched.
    // This is a Double (capital D), the object wrapper, NOT a primitive double.
    // A wrapper can be null; a primitive cannot. null lets us say "no data at all,"
    // which is different from a real reading of 0.0 (a genuine freezing temperature).
    private final Double temperature;

    // "Feels-like" temperature in Fahrenheit — factors in humidity and wind.
    // This is the reading the weather feature exists for. null if unavailable.
    private final Double apparentTemperature;

    // Short human-readable sky description, e.g. "Overcast", or null if unavailable.
    private final String weatherCondition;

    /*
     * Constructor — the only way to set this object's values.
     * After this runs, the three fields are locked for the object's lifetime
     * because they are final.
     */
    public WeatherData(Double temperature, Double apparentTemperature, String weatherCondition) {
        this.temperature = temperature;
        this.apparentTemperature = apparentTemperature;
        this.weatherCondition = weatherCondition;
    }

    // Returns the air temperature in Fahrenheit, or null if it wasn't recorded.
    public Double getTemperature() {
        return temperature;
    }

    // Returns the feels-like temperature in Fahrenheit, or null if it wasn't recorded.
    public Double getApparentTemperature() {
        return apparentTemperature;
    }

    // Returns the sky description, or null if it wasn't recorded.
    public String getWeatherCondition() {
        return weatherCondition;
    }
}
