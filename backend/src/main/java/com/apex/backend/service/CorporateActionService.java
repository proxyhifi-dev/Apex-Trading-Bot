package com.apex.backend.service;

import com.apex.backend.model.Candle;
import com.apex.backend.model.CorporateAction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CorporateActionService {

    private final DataAdjustmentService dataAdjustmentService;

    public List<Candle> applyAdjustments(List<Candle> candles, List<CorporateAction> actions) {
        return dataAdjustmentService.applyCorporateActions(candles, actions);
    }
}
