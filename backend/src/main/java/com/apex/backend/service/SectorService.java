package com.apex.backend.service;

import com.apex.backend.model.SectorMapping;
import com.apex.backend.repository.SectorMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

@Service
@RequiredArgsConstructor
public class SectorService {

    private final SectorMappingRepository repository;

    @PostConstruct
    public void initDefaultSectors() {
        if (repository.count() == 0) {
            save("NSE:HDFCBANK-EQ", "FINANCE");
            save("NSE:RELIANCE-EQ", "ENERGY");
            save("NSE:TCS-EQ", "IT");
            save("NSE:INFY-EQ", "IT");
            save("NSE:TATAMOTORS-EQ", "AUTO");
        }
    }

    public String getSector(String symbol) {
        return repository.findBySymbol(symbol)
                .map(SectorMapping::getSector)
                .orElse("OTHERS");
    }

    private void save(String symbol, String sector) {
        repository.save(SectorMapping.builder().symbol(symbol).sector(sector).build());
    }
}