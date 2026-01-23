package com.apex.backend.repository;

import com.apex.backend.model.Instrument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InstrumentRepository extends JpaRepository<Instrument, Long> {
    Optional<Instrument> findBySymbolIgnoreCase(String symbol);
    Optional<Instrument> findByTradingSymbolIgnoreCase(String tradingSymbol);
}
