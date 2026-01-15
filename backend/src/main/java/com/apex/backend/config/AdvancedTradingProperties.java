package com.apex.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "apex.advanced")
@Data
public class AdvancedTradingProperties {

    private MarketRegime marketRegime = new MarketRegime();
    private MacdConfirmation macdConfirmation = new MacdConfirmation();
    private CandleConfirmation candleConfirmation = new CandleConfirmation();
    private MultiTimeframeMomentum multiTimeframeMomentum = new MultiTimeframeMomentum();
    private Risk risk = new Risk();
    private ExecutionCost executionCost = new ExecutionCost();
    private Liquidity liquidity = new Liquidity();
    private Backtest backtest = new Backtest();
    private Broker broker = new Broker();
    private Audit audit = new Audit();

    @Data
    public static class MarketRegime {
        private double trendingAdxThreshold = 25.0;
        private double strongTrendAdx = 30.0;
        private double highVolAtrPercent = 3.0;
        private double lowVolAtrPercent = 1.0;
        private boolean chopFilterEnabled = false;
        private int chopPeriod = 14;
        private double choppyThreshold = 61.8;
    }

    @Data
    public static class MacdConfirmation {
        private int divergenceLookback = 30;
        private int histogramSlopeLookback = 3;
    }

    @Data
    public static class CandleConfirmation {
        private int requiredCandles = 3;
        private boolean volumeConfirmation = true;
        private double volumeMultiplier = 1.2;
        private int avgVolumePeriod = 20;
    }

    @Data
    public static class MultiTimeframeMomentum {
        private Map<String, Double> weights = Map.of(
                "5m", 0.4,
                "15m", 0.3,
                "1h", 0.2,
                "1d", 0.1
        );
        private double conflictPenalty = 0.15;
    }

    @Data
    public static class Risk {
        private double maxPortfolioHeatPct = 0.06;
        private double correlationThreshold = 0.7;
        private int correlationLookback = 50;
        private int kellyLookbackTrades = 50;
        private double kellyFraction = 0.25;
        private double maxDailyLossPct = 0.02;
        private double maxWeeklyLossPct = 0.10;
        private double maxMonthlyLossPct = 0.15;
        private CircuitBreakers circuitBreakers = new CircuitBreakers();
    }

    @Data
    public static class CircuitBreakers {
        private boolean enabled = false;
        private int maxConsecutiveLosses = 2;
        private int cooldownMinutes = 45;
    }

    @Data
    public static class ExecutionCost {
        private double spreadBps = 5.0;
        private double slippageAtrPct = 0.1;
        private double commissionPct = 0.0003;
        private double sttPct = 0.00025;
        private double txnPct = 0.00003;
        private double sebiPct = 0.000001;
        private double gstPct = 0.18;
    }

    @Data
    public static class Liquidity {
        private double minRupeeVolume = 2_000_000.0;
        private double maxSpreadPct = 0.3;
        private double maxOrderPctDailyVolume = 0.02;
        private double impactLambda = 0.1;
        private boolean gateEnabled = false;
    }

    @Data
    public static class Backtest {
        private int maxBarsInTrade = 50;
        private int inSampleBars = 200;
        private int outSampleBars = 100;
        private int decayLookbackTrades = 20;
        private int timeStopBars = 12;
        private double timeStopMinMoveR = 0.3;
        private double chandelierAtrMult = 3.0;
    }

    @Data
    public static class Broker {
        private int failureThreshold = 3;
        private int coolDownSeconds = 120;
    }

    @Data
    public static class Audit {
        private int retentionDays = 30;
    }
}
