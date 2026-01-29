package com.apex.backend.service;

import com.apex.backend.exception.BadRequestException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class UniverseService {

    private final Map<String, List<String>> cache = new ConcurrentHashMap<>();

    public List<String> loadUniverse(String universeName) {
        String normalized = normalize(universeName);
        return cache.computeIfAbsent(normalized, this::loadUniverseFile);
    }

    private List<String> loadUniverseFile(String normalized) {
        String resourcePath = switch (normalized) {
            case "NIFTY100" -> "universe/nifty100.txt";
            default -> null;
        };
        if (resourcePath == null) {
            throw new BadRequestException("Unknown universe: " + normalized);
        }
        try {
            ClassPathResource resource = new ClassPathResource(resourcePath);
            if (!resource.exists()) {
                throw new BadRequestException("Universe data missing for " + normalized);
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                LinkedHashSet<String> symbols = reader.lines()
                        .map(String::trim)
                        .filter(line -> !line.isBlank())
                        .filter(line -> !line.startsWith("#"))
                        .map(line -> line.toUpperCase(Locale.ROOT))
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                if (symbols.isEmpty()) {
                    throw new BadRequestException("Universe is empty for " + normalized);
                }
                return List.copyOf(symbols);
            }
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BadRequestException("Failed to load universe " + normalized);
        }
    }

    private String normalize(String universeName) {
        if (universeName == null || universeName.isBlank()) {
            return "NIFTY100";
        }
        return universeName.trim().toUpperCase(Locale.ROOT);
    }
}
