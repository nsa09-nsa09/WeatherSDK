package org.example;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for creating and managing WeatherSDK instances.
 * Ensures that only one instance exists per API key.
 */
public class WeatherSDKFactory {
    private static final Map<String, WeatherSDK> instances = new ConcurrentHashMap<>();

    /**
     * Returns a WeatherSDK instance for the specified API key.
     * If an instance with the same API key exists, it is returned; otherwise, a new one is created.
     *
     * @param apiKey      Your OpenWeather API key.
     * @return A WeatherSDK instance.
     */
    public static synchronized WeatherSDK getInstance(String apiKey) {
        if (instances.containsKey(apiKey)) {
            return instances.get(apiKey);
        }
        WeatherSDK sdk = new WeatherSDK(apiKey);
        instances.put(apiKey, sdk);
        return sdk;
    }

    /**
     * Deletes the WeatherSDK instance associated with the given API key.
     *
     * @param apiKey Your OpenWeather API key.
     * @return True if the instance was removed; false otherwise.
     */
    public static synchronized boolean deleteInstance(String apiKey) {
        WeatherSDK sdk = instances.remove(apiKey);
        if (sdk != null) {
            sdk.shutdown();
            return true;
        }
        return false;
    }
}
