package dev.paydeck.api.push;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class PushConfig {

    private static final Logger log = LoggerFactory.getLogger(PushConfig.class);

    /**
     * Real push notifications when FIREBASE_SERVICE_ACCOUNT_FILE points to a
     * Firebase key. Without one, notifications are only written to the log,
     * which is all you need while building.
     */
    @Bean
    PushSender pushSender(@Value("${paydeck.push.firebase-service-account-file:}") String serviceAccountFile,
                          RestClient.Builder http) throws IOException {
        if (serviceAccountFile.isBlank()) {
            return (token, title, body) -> {
                log.info("Push (not sent, Firebase is not set up): {}: {}", title, body);
                return PushSender.Result.SENT;
            };
        }
        return new FcmPushSender(serviceAccountFile, http);
    }
}
