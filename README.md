# WeatherSDK

WeatherSDK is a Java library for querying the OpenWeatherMap API and retrieving weather forecast data in JSON format. It automatically polls for updates in the background to ensure that the weather data is always fresh.

## Features

- **Automatic Polling:** Weather data for each requested city is updated periodically (every 5 minutes).
- **Thread-Safe:** Uses `ConcurrentHashMap` and scheduled tasks for safe, concurrent data handling.
- **Simple JSON Transformation:** Easily convert the detailed API response into a simplified JSON format.
- **Easy Integration:** Retrieve weather data for any city with minimal setup.

## Installation

### Clone the Repository

Clone the project from GitHub:
```
git clone https://github.com/yourusername/WeatherSDK.git
cd WeatherSDK
```

Build and Install Locally (Gradle)
Make sure you have Gradle installed (or use the provided Gradle Wrapper). In the project root directory, run:
```
./gradlew publishToMavenLocal
```
This command will build the project and publish the library to your local Maven repository.

Publishing to a Remote Repository
If you want to publish the library to a remote repository, configure your build.gradle with the appropriate repository information, then run:
```
./gradlew publish
```

Adding WeatherSDK to Your Gradle Project
In your Gradle project's build.gradle file, add mavenLocal() to your repositories and declare the dependency:
```
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation 'org.example:WeatherSDK:1.0.0'
}
```

Usage
Retrieving Weather Data
Below is an example of how to retrieve weather data for a city:

```
import com.fasterxml.jackson.databind.JsonNode;

public class Main {
    public static void main(String[] args) {
        // Replace with your actual OpenWeather API key.
        String apiKey = "aa9fd131539f49b3cfade73169ec9154";
        String city = "London";

        // Obtain a WeatherSDK instance via the factory (polling mode enabled).
        WeatherSDK weatherSDK = WeatherSDKFactory.getInstance(apiKey);

        try {
            // Retrieve weather data for the specified city.
            JsonNode weatherData = weatherSDK.getWeather(city);
            System.out.println("Weather Forecast for " + city + ":");
            System.out.println(weatherData.toPrettyString());
        } catch (WeatherSDKException e) {
            System.err.println("Error fetching weather data: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Shutdown the SDK and remove the instance.
            weatherSDK.shutdown();
            WeatherSDKFactory.deleteInstance(apiKey);
        }
    }
}
```