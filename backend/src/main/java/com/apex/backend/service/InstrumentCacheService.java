package com.apex.backend.service;

import com.apex.backend.dto.InstrumentDTO;
import com.apex.backend.model.InstrumentDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class InstrumentCacheService {

    private static final String DEFAULT_RESOURCE = "instruments.csv";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${apex.instruments.source:}")
    private String source;

    private final Map<String, InstrumentDefinition> cache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        refresh();
    }

    public void refresh() {
        List<InstrumentDefinition> instruments = load();
        cache.clear();
        for (InstrumentDefinition instrument : instruments) {
            if (instrument.getSymbol() != null) {
                cache.put(instrument.getSymbol().toUpperCase(Locale.ROOT), instrument);
            }
        }
        log.info("Loaded {} instruments", cache.size());
    }

    public Optional<InstrumentDefinition> findBySymbol(String symbol) {
        if (!StringUtils.hasText(symbol)) {
            return Optional.empty();
        }
        return Optional.ofNullable(cache.get(symbol.toUpperCase(Locale.ROOT)));
    }

    public List<InstrumentDTO> search(String query, int limit) {
        if (!StringUtils.hasText(query)) {
            return Collections.emptyList();
        }
        String q = query.toLowerCase(Locale.ROOT);
        return cache.values().stream()
                .filter(item -> item.getSymbol().toLowerCase(Locale.ROOT).contains(q)
                        || (item.getName() != null && item.getName().toLowerCase(Locale.ROOT).contains(q)))
                .limit(limit)
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<InstrumentDefinition> listDefinitions(int limit) {
        return cache.values().stream()
                .sorted((left, right) -> {
                    String l = left.getSymbol() == null ? "" : left.getSymbol();
                    String r = right.getSymbol() == null ? "" : right.getSymbol();
                    return l.compareToIgnoreCase(r);
                })
                .limit(limit)
                .toList();
    }

    public InstrumentDTO toDto(InstrumentDefinition instrument) {
        return InstrumentDTO.builder()
                .symbol(instrument.getSymbol())
                .name(instrument.getName())
                .exchange(instrument.getExchange())
                .segment(instrument.getSegment())
                .tickSize(instrument.getTickSize())
                .lotSize(instrument.getLotSize())
                .isin(instrument.getIsin())
                .build();
    }

    private List<InstrumentDefinition> load() {
        if (StringUtils.hasText(source)) {
            try {
                if (source.toLowerCase(Locale.ROOT).endsWith(".json")) {
                    return loadJson(new URL(source));
                }
                return loadCsv(new URL(source));
            } catch (Exception e) {
                log.warn("Failed to load instruments from {}: {}", source, e.getMessage());
            }
        }
        ClassPathResource resource = new ClassPathResource(DEFAULT_RESOURCE);
        if (!resource.exists()) {
            return List.of();
        }
        try {
            return loadCsv(resource);
        } catch (Exception e) {
            log.warn("Failed to load instruments from classpath: {}", e.getMessage());
            return List.of();
        }
    }

    private List<InstrumentDefinition> loadCsv(ClassPathResource resource) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            return parseCsv(reader);
        }
    }

    private List<InstrumentDefinition> loadCsv(URL url) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
            return parseCsv(reader);
        }
    }

    private List<InstrumentDefinition> parseCsv(BufferedReader reader) throws Exception {
        List<InstrumentDefinition> instruments = new ArrayList<>();
        String header = reader.readLine();
        if (header == null) {
            return instruments;
        }
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split(",");
            instruments.add(InstrumentDefinition.builder()
                    .symbol(read(parts, 0))
                    .name(read(parts, 1))
                    .exchange(read(parts, 2))
                    .segment(read(parts, 3))
                    .tickSize(parseDecimal(read(parts, 4)))
                    .lotSize(parseInt(read(parts, 5)))
                    .isin(read(parts, 6))
                    .build());
        }
        return instruments;
    }

    private List<InstrumentDefinition> loadJson(URL url) throws Exception {
        List<Map<String, Object>> data = objectMapper.readValue(url, List.class);
        List<InstrumentDefinition> instruments = new ArrayList<>();
        for (Map<String, Object> item : data) {
            instruments.add(InstrumentDefinition.builder()
                    .symbol(value(item, "symbol"))
                    .name(value(item, "name"))
                    .exchange(value(item, "exchange"))
                    .segment(value(item, "segment"))
                    .tickSize(parseDecimal(value(item, "tickSize")))
                    .lotSize(parseInt(value(item, "lotSize")))
                    .isin(value(item, "isin"))
                    .build());
        }
        return instruments;
    }

    private String read(String[] parts, int index) {
        if (index >= parts.length) {
            return null;
        }
        String value = parts[index].trim();
        return value.isBlank() ? null : value;
    }

    private BigDecimal parseDecimal(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return new BigDecimal(value.trim());
    }

    private Integer parseInt(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return Integer.parseInt(value.trim());
    }

    private String value(Map<String, Object> item, String key) {
        Object value = item.get(key);
        return value == null ? null : value.toString();
    }
}
