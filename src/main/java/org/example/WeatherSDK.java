package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * WeatherSDK provides an interface for querying the OpenWeatherMap API.
 * It always works in polling mode.
 *
 * <p>Example usage:
 * <pre>
 *   WeatherSDK sdk = WeatherSDKFactory.getInstance("YOUR_API_KEY");
 *   JsonNode weather = sdk.getWeather("London");
 *   System.out.println(weather.toPrettyString());
 *   sdk.shutdown();
 *   WeatherSDKFactory.deleteInstance("YOUR_API_KEY");
 * </pre>
 * </p>
 */
public class WeatherSDK {
    private static final String BASE_URL = "https://api.openweathermap.org/data/2.5/forecast";
    private static final int CACHE_EXPIRATION = 10 * 60 * 1000; // 10 minutes
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final String apiKey;
    private final ScheduledExecutorService scheduler;

    private final Map<String, AtomicReference<CachedWeather>> cache;

    /**
     * Constructs a WeatherSDK instance.
     * Use WeatherSDKFactory to obtain an instance.
     *
     * @param apiKey Your OpenWeather API key (must not be null or empty).
     */
    WeatherSDK(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("API key must not be null or empty.");
        }
        this.apiKey = apiKey;
        this.scheduler = Executors.newScheduledThreadPool(3);
        this.cache = new ConcurrentHashMap<>();
    }

    /**
     * Retrieves the weather data for the specified city in JSON format.
     * If valid cached data exists (less than 10 minutes old), that is returned.
     *
     * @param city The name of the city.
     * @return JsonNode representing the weather data.
     */
    public JsonNode getWeather(String city) throws WeatherSDKException {
        if (city == null || city.trim().isEmpty()) {
            throw new IllegalArgumentException("City must not be null or empty.");
        }

        AtomicReference<CachedWeather> ref = cache.computeIfAbsent(city, key -> {
            try {
                JsonNode data = fetchWeather(city);
                AtomicReference<CachedWeather> newRef = new AtomicReference<>(new CachedWeather(data));
                // Schedule background update every 5 minutes
                scheduler.scheduleAtFixedRate(() -> updateWeather(city),
                        0, 5, TimeUnit.MINUTES);
                return newRef;
            } catch (WeatherSDKException e) {
                throw new RuntimeException("Failed to fetch weather for " + city, e);
            }
        });

        CachedWeather cached = ref.get();
        if (System.currentTimeMillis() - cached.timestamp >= CACHE_EXPIRATION) {
            updateWeather(city);
        }
        return parseAnswer(ref.get().data);
    }

    /**
     * Fetches new weather data from the API and updates the cached data.
     * Если обновление не удалось, возвращаются уже кэшированные данные (если они имеются).
     *
     * @param city The name of the city.
     * @return JsonNode with simplified weather data.
     */
    private JsonNode updateWeather(String city) {
        try {
            JsonNode data = fetchWeather(city);
            CachedWeather newCached = new CachedWeather(data);
            AtomicReference<CachedWeather> ref = cache.get(city);
            if (ref != null) {
                ref.set(newCached);
            } else {
                cache.put(city, new AtomicReference<>(newCached));
            }
            return parseAnswer(data);
        } catch (WeatherSDKException e) {
            System.err.println("Failed to update weather data for " + city + ": " + e.getMessage());
            AtomicReference<CachedWeather> ref = cache.get(city);
            if (ref != null) {
                return parseAnswer(ref.get().data);
            }
            throw new RuntimeException("No weather data available for " + city, e);
        }
    }

    /**
     * Fetches weather data from the OpenWeatherMap API for the given city.
     *
     * @param city The name of the city.
     * @return JsonNode representing weather data.
     * @throws WeatherSDKException If an I/O error occurs or the API returns an error.
     */
    private JsonNode fetchWeather(String city) throws WeatherSDKException {
        try {
            String urlString = addParam(BASE_URL, "q", city);
            urlString = addParam(urlString, "appid", apiKey);

            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new WeatherSDKException("Error fetching weather data: HTTP " + responseCode);
            }
            try (InputStream in = conn.getInputStream()) {
                return objectMapper.readTree(in);
            }
        } catch (IOException e) {
            throw new WeatherSDKException("Error fetching weather data: " + e.getMessage(), e);
        }
    }

    /**
     * Helper method to append a query parameter to a URL.
     *
     * @param url   The base URL.
     * @param param The parameter name.
     * @param value The parameter value.
     * @return The URL with the appended query parameter.
     */
    private String addParam(String url, String param, String value) {
        try {
            String encodedValue = URLEncoder.encode(value, StandardCharsets.UTF_8.name());
            char separator = url.contains("?") ? '&' : '?';
            return url + separator + param + "=" + encodedValue;
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("UTF-8 encoding not supported", e);
        }
    }

    /**
     * Parses the full API JSON response into a simplified JSON structure.
     *
     * The resulting JSON structure will be:
     * <pre>
     * {
     *   "weather": {"main": "...", "description": "..."},
     *   "temperature": {"temp": ..., "feels_like": ...},
     *   "visibility": ...,
     *   "wind": {"speed": ...},
     *   "datetime": ...,
     *   "sys": {"sunrise": ..., "sunset": ...},
     *   "timezone": ...,
     *   "name": "..."
     * }
     * </pre>
     *
     * @param rootNode The full JSON response from the forecast API.
     * @return A simplified JSON object.
     */
    private JsonNode parseAnswer(JsonNode rootNode) {
        JsonNode firstForecast = rootNode.path("list").get(0);
        if (firstForecast == null) {
            throw new IllegalArgumentException("Нет данных прогноза в JSON.");
        }
        ObjectNode expected = JsonNodeFactory.instance.objectNode();

        JsonNode weatherArray = firstForecast.path("weather");
        if (weatherArray.isArray() && weatherArray.size() > 0) {
            JsonNode weather = weatherArray.get(0);
            ObjectNode weatherObj = expected.putObject("weather");
            weatherObj.put("main", weather.path("main").asText());
            weatherObj.put("description", weather.path("description").asText());
        }

        ObjectNode temperatureObj = expected.putObject("temperature");
        JsonNode mainNode = firstForecast.path("main");
        temperatureObj.put("temp", mainNode.path("temp").asDouble());
        temperatureObj.put("feels_like", mainNode.path("feels_like").asDouble());

        expected.put("visibility", firstForecast.path("visibility").asInt());

        ObjectNode windObj = expected.putObject("wind");
        windObj.put("speed", firstForecast.path("wind").path("speed").asDouble());

        expected.put("datetime", firstForecast.path("dt").asLong());

        JsonNode cityNode = rootNode.path("city");
        ObjectNode sysObj = expected.putObject("sys");
        sysObj.put("sunrise", cityNode.path("sunrise").asLong());
        sysObj.put("sunset", cityNode.path("sunset").asLong());

        expected.put("timezone", cityNode.path("timezone").asInt());
        expected.put("name", cityNode.path("name").asText());

        return expected;
    }

    /**
     * Shuts down the SDK, canceling all background update tasks.
     */
    public void shutdown() {
        scheduler.shutdown();
    }

    /**
     * Inner class for caching weather data along with a timestamp.
     */
    private static class CachedWeather {
        final JsonNode data;
        final long timestamp;

        CachedWeather(JsonNode data) {
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }
    }
}
