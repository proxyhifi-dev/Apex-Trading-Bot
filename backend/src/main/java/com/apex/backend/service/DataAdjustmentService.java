package com.apex.backend.service;

import com.apex.backend.model.Candle;
import com.apex.backend.model.CorporateAction;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class DataAdjustmentService {

    public List<Candle> applyCorporateActions(List<Candle> candles, List<CorporateAction> actions) {
        if (actions == null || actions.isEmpty()) {
            return candles;
        }
        List<CorporateAction> sorted = new ArrayList<>(actions);
        sorted.sort(Comparator.comparing(CorporateAction::actionDate));
        List<Candle> adjusted = new ArrayList<>();
        for (Candle candle : candles) {
            double factor = 1.0;
            double volumeFactor = 1.0;
            for (CorporateAction action : sorted) {
                if (candle.getTimestamp() == null) {
                    continue;
                }
                if (candle.getTimestamp().toLocalDate().isBefore(action.actionDate())) {
                    if (action.type() == CorporateAction.Type.SPLIT) {
                        factor *= action.ratio();
                        volumeFactor /= action.ratio();
                    }
                }
            }
            Candle adjustedCandle = new Candle(
                    candle.getOpen() * factor,
                    candle.getHigh() * factor,
                    candle.getLow() * factor,
                    candle.getClose() * factor,
                    Math.round(candle.getVolume() * volumeFactor),
                    candle.getTimestamp()
            );
            adjusted.add(adjustedCandle);
        }
        return adjusted;
    }
}
