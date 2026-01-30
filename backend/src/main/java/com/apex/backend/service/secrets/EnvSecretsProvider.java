package com.apex.backend.service.secrets;

import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class EnvSecretsProvider implements SecretsProvider {
    @Override
    public Optional<String> getSecret(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value);
    }
}
