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
    private static final int CACHE_EXPIRATION = 10 * 60 * 1000; // 10 minutes in milliseconds
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final String apiKey;
    private final ScheduledExecutorService scheduler;
    private final Map<String, CachedWeather> cache;
    private final Map<String, ScheduledFuture<?>> pollingTasks;

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
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.pollingTasks = new ConcurrentHashMap<>();
        this.cache = new ConcurrentHashMap<>();
    }

    /**
     * Retrieves the weather data for the specified city in JSON format.
     * If valid cached data exists (less than 10 minutes old), that is returned.
     *
     * @param city The name of the city.
     * @return JsonNode representing the weather data.
     * @throws WeatherSDKException If an error occurs (e.g., invalid API key or network issue).
     */
    public JsonNode getWeather(String city) throws WeatherSDKException {
        if (city == null || city.trim().isEmpty()) {
            throw new IllegalArgumentException("City must not be null or empty.");
        }
        // Всегда запускаем задачу обновления данных для города, если она ещё не запущена.
        if (!pollingTasks.containsKey(city)) {
            ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> updateWeather(city),
                    0, 5, TimeUnit.MINUTES);
            pollingTasks.put(city, future);
        }
        CachedWeather cached = cache.get(city);
        JsonNode baseWeatherData;
        if (cached != null && System.currentTimeMillis() - cached.timestamp < CACHE_EXPIRATION) {
            baseWeatherData = cached.data;
        } else {
            baseWeatherData = fetchWeather(city);
            cache.put(city, new CachedWeather(baseWeatherData));
        }
        return parseAnswer(baseWeatherData);
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
            String urlString = BASE_URL;
            urlString = addParam(urlString, "q", city);
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
        String encodedValue;
        try {
            encodedValue = URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("UTF-8 encoding not supported", e);
        }
        char separator = url.contains("?") ? '&' : '?';
        return url + separator + param + "=" + encodedValue;
    }

    /**
     * Updates the cached weather data for a given city.
     *
     * @param city The city to update.
     */
    private void updateWeather(String city) {
        try {
            JsonNode data = fetchWeather(city);
            cache.put(city, new CachedWeather(data));
        } catch (WeatherSDKException e) {
            System.err.println("Failed to update weather data for " + city + ": " + e.getMessage());
        }
    }

    /**
     * Parses the full API JSON response into a simplified JSON structure.
     * This method is called every time the weather data is retrieved.
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

        // Create "weather" object using the first element of the "weather" array.
        JsonNode weatherArray = firstForecast.path("weather");
        if (weatherArray.isArray() && weatherArray.size() > 0) {
            JsonNode weather = weatherArray.get(0);
            ObjectNode weatherObj = expected.putObject("weather");
            weatherObj.put("main", weather.path("main").asText());
            weatherObj.put("description", weather.path("description").asText());
        }

        // Create "temperature" object.
        ObjectNode temperatureObj = expected.putObject("temperature");
        JsonNode mainNode = firstForecast.path("main");
        temperatureObj.put("temp", mainNode.path("temp").asDouble());
        temperatureObj.put("feels_like", mainNode.path("feels_like").asDouble());

        // Add "visibility" from the forecast.
        expected.put("visibility", firstForecast.path("visibility").asInt());

        // Create "wind" object.
        ObjectNode windObj = expected.putObject("wind");
        windObj.put("speed", firstForecast.path("wind").path("speed").asDouble());

        // Add "datetime" (forecast time).
        expected.put("datetime", firstForecast.path("dt").asLong());

        // Create "sys" object using city data (sunrise and sunset).
        JsonNode cityNode = rootNode.path("city");
        ObjectNode sysObj = expected.putObject("sys");
        sysObj.put("sunrise", cityNode.path("sunrise").asLong());
        sysObj.put("sunset", cityNode.path("sunset").asLong());

        // Add "timezone" and "name" from city data.
        expected.put("timezone", cityNode.path("timezone").asInt());
        expected.put("name", cityNode.path("name").asText());

        return expected;
    }

    /**
     * Shuts down the SDK, canceling any ongoing polling tasks.
     */
    public void shutdown() {
        for (ScheduledFuture<?> future : pollingTasks.values()) {
            future.cancel(true);
        }
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
