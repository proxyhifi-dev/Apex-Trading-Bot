package com.apex.backend.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@Converter
@RequiredArgsConstructor
public class TokenEncryptionConverter implements AttributeConverter<String, String> {

    private final TokenEncryptionService tokenEncryptionService;

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return tokenEncryptionService.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return tokenEncryptionService.decrypt(dbData);
    }
}
