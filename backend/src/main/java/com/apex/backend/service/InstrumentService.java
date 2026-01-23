package com.apex.backend.service;

import com.apex.backend.model.Instrument;
import com.apex.backend.repository.InstrumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class InstrumentService {

    private final InstrumentRepository instrumentRepository;

    public Optional<Instrument> findBySymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return Optional.empty();
        }
        return instrumentRepository.findBySymbolIgnoreCase(symbol.trim());
    }

    public Optional<Instrument> findByTradingSymbol(String tradingSymbol) {
        if (tradingSymbol == null || tradingSymbol.isBlank()) {
            return Optional.empty();
        }
        return instrumentRepository.findByTradingSymbolIgnoreCase(tradingSymbol.trim());
    }

    public Optional<String> resolveTradingSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return Optional.empty();
        }
        String trimmed = symbol.trim();
        if (trimmed.contains(":")) {
            return Optional.of(trimmed);
        }
        return findBySymbol(trimmed)
                .or(() -> findByTradingSymbol(trimmed))
                .map(Instrument::getTradingSymbol);
    }

    public void logMissingInstrument(String symbol) {
        log.warn("Instrument lookup failed for symbol={}", symbol);
    }
}
