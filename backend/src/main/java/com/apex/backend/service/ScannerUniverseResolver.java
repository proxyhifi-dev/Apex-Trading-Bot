package com.apex.backend.service;

import com.apex.backend.config.StrategyConfig;
import com.apex.backend.dto.ScanRequest;
import com.apex.backend.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ScannerUniverseResolver {

    private final StrategyConfig strategyConfig;
    private final InstrumentService instrumentService;

    public List<String> resolveUniverse(ScanRequest request) {
        List<String> raw = switch (request.getUniverse()) {
            case CUSTOM -> resolveCustom(request);
            case NIFTY50 -> normalizeList(strategyConfig.getScanner().getUniverses().getNifty50());
            case NIFTY200 -> normalizeList(strategyConfig.getScanner().getUniverses().getNifty200());
        };
        if (raw.isEmpty()) {
            throw new BadRequestException("Universe is empty or not configured");
        }
        return raw.stream()
                .map(this::resolveTradingSymbol)
                .flatMap(java.util.Optional::stream)
                .distinct()
                .toList();
    }

    private List<String> resolveCustom(ScanRequest request) {
        List<String> symbols = request.getSymbols();
        if (symbols == null || symbols.isEmpty()) {
            throw new BadRequestException("Custom universe requires symbols");
        }
        return normalizeList(symbols);
    }

    private List<String> normalizeList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        List<String> resolved = new ArrayList<>();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String trimmed = value.trim();
            if (!trimmed.isBlank()) {
                resolved.add(trimmed);
            }
        }
        return resolved;
    }

    private java.util.Optional<String> resolveTradingSymbol(String symbol) {
        java.util.Optional<String> resolved = instrumentService.resolveTradingSymbol(symbol);
        if (resolved.isEmpty()) {
            instrumentService.logMissingInstrument(symbol);
        }
        return resolved;
    }
}
