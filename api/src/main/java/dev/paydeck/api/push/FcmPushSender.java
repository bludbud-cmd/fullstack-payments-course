package dev.paydeck.api.push;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Sends notifications to Android phones through Firebase Cloud Messaging's
 * HTTP v1 API. The server signs in to Google with a service account key
 * (downloaded from the Firebase console), and Google gives it a short-lived
 * access token to send with each message.
 */
public class FcmPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(FcmPushSender.class);
    private static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";

    private final GoogleCredentials credentials;
    private final String projectId;
    private final RestClient http;

    public FcmPushSender(String serviceAccountFile, RestClient.Builder http) throws IOException {
        try (InputStream in = new FileInputStream(serviceAccountFile)) {
            ServiceAccountCredentials account = ServiceAccountCredentials.fromStream(in);
            this.credentials = account.createScoped(List.of(SCOPE));
            this.projectId = account.getProjectId();
        }
        this.http = http.baseUrl("https://fcm.googleapis.com").build();
    }

    @Override
    public Result send(String deviceToken, String title, String body) {
        try {
            credentials.refreshIfExpired();
            http.post()
                    .uri("/v1/projects/{project}/messages:send", projectId)
                    .header("Authorization", "Bearer " + credentials.getAccessToken().getTokenValue())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("message", Map.of(
                            "token", deviceToken,
                            "notification", Map.of("title", title, "body", body))))
                    .retrieve()
                    .toBodilessEntity();
            return Result.SENT;
        } catch (HttpClientErrorException e) {
            // 404 UNREGISTERED: the token no longer belongs to an installed app.
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Result.TOKEN_GONE;
            }
            log.warn("Firebase refused a notification: {}", e.getResponseBodyAsString());
            return Result.FAILED;
        } catch (IOException | RestClientException e) {
            log.warn("Could not send a notification: {}", e.getMessage());
            return Result.FAILED;
        }
    }
}
