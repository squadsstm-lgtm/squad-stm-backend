package com.squad.backend.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Configuration
@Slf4j
public class FirebaseConfig {

    @Value("${firebase.config-path}")
    private Resource firebaseConfigPath;

    @Value("${firebase.credentials-json:}")
    private String firebaseCredentialsJson;

    @PostConstruct
    public void initialize() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                InputStream serviceAccount = resolveCredentials();

                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .build();

                FirebaseApp.initializeApp(options);
                log.info("Firebase Admin SDK initialized successfully");
            } else {
                log.info("Firebase Admin SDK already initialized");
            }
        } catch (Exception e) {
            log.error("Error initializing Firebase Admin SDK: ", e);
            throw new RuntimeException("Failed to initialize Firebase Admin SDK", e);
        }
    }

    private InputStream resolveCredentials() throws Exception {
        if (firebaseCredentialsJson != null && !firebaseCredentialsJson.isBlank()) {
            log.info("Loading Firebase credentials from FIREBASE_CREDENTIALS_JSON env var");
            return new ByteArrayInputStream(firebaseCredentialsJson.getBytes(StandardCharsets.UTF_8));
        }
        log.info("Loading Firebase credentials from file: {}", firebaseConfigPath.getDescription());
        return firebaseConfigPath.getInputStream();
    }
}
