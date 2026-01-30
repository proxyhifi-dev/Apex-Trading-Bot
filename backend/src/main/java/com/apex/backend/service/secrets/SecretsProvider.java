package com.apex.backend.service.secrets;

import java.util.Optional;

public interface SecretsProvider {
    Optional<String> getSecret(String key);
}
