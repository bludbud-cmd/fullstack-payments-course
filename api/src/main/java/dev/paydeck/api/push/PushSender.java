package dev.paydeck.api.push;

/** Sends one notification to one phone. */
public interface PushSender {

    enum Result {
        SENT,
        /** The app was uninstalled or the token expired: forget this device. */
        TOKEN_GONE,
        FAILED
    }

    Result send(String deviceToken, String title, String body);
}
