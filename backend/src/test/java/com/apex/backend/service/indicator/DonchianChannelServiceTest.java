package com.apex.backend.service.indicator;

import com.apex.backend.model.Candle;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DonchianChannelServiceTest {

    @Test
    void excludesCurrentCandle() {
        DonchianChannelService service = new DonchianChannelService();
        List<Candle> candles = new ArrayList<>();
        LocalDateTime start = LocalDateTime.now().minusMinutes(25);
        candles.add(new Candle(100, 105, 101, 102, 1000, start));
        candles.add(new Candle(102, 106, 102, 104, 1000, start.plusMinutes(5)));
        candles.add(new Candle(104, 110, 103, 108, 1000, start.plusMinutes(10)));
        candles.add(new Candle(108, 112, 104, 111, 1000, start.plusMinutes(15)));
        candles.add(new Candle(111, 150, 90, 140, 1000, start.plusMinutes(20)));

        DonchianChannelService.Donchian donchian = service.calculate(candles, 3);

        assertThat(donchian.upper()).isEqualTo(112);
        assertThat(donchian.lower()).isEqualTo(102);
    }
}
