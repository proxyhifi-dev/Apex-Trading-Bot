package com.apex.backend.service.secrets;

import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SecretsManagerService {

    private final List<SecretsProvider> providers;
    private final Environment environment;

    public String resolve(String key, String fallback) {
        Optional<String> fromProviders = providers.stream()
                .map(provider -> provider.getSecret(key))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
        if (fromProviders.isPresent()) {
            return fromProviders.get();
        }
        String property = environment.getProperty(key);
        return property != null && !property.isBlank() ? property : fallback;
    }
}
