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
import org.example.WeatherSDK;
import org.example.WeatherSDKException;

public class WeatherExample {
    public static void main(String[] args) {
        try {
            // Replace "YOUR_API_KEY" with your actual OpenWeatherMap API key.
            WeatherSDK sdk = new WeatherSDK("YOUR_API_KEY");
            JsonNode weather = sdk.getWeather("London");
            System.out.println(weather.toPrettyString());
            sdk.shutdown();
        } catch (WeatherSDKException e) {
            e.printStackTrace();
        }
    }
}
```