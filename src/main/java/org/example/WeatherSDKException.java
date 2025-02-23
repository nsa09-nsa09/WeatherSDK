package org.example;

/**
 * Custom exception for errors occurring in WeatherSDK.
 */
public class WeatherSDKException extends Exception {
    public WeatherSDKException(String message) {
        super(message);
    }
    public WeatherSDKException(String message, Throwable cause) {
        super(message, cause);
    }
}
