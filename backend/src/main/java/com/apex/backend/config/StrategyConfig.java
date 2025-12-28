package com.apex.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "apex")
@Data
public class StrategyConfig {

    // These fields match what RiskManagementEngine expects
    private Trading trading = new Trading();
    private Strategy strategy = new Strategy();
    private Risk risk = new Risk(); // ✅ This generates getRisk()
    private Scoring scoring = new Scoring();
    private Sizing sizing = new Sizing();
    private Exit exit = new Exit();
    private Scanner scanner = new Scanner();

    @Data
    public static class Trading {
        private double capital;
        private double riskPercent;
        private int maxPositions;
        private Universe universe = new Universe();
    }

    @Data
    public static class Universe {
        private List<String> symbols = new ArrayList<>();
        private boolean useFallback = true;
    }

    @Data
    public static class Strategy {
        private Macd macd = new Macd();
        private Adx adx = new Adx();
        private Rsi rsi = new Rsi();
        private Atr atr = new Atr();
        private Squeeze squeeze = new Squeeze();
    }

    @Data
    public static class Macd {
        private int fastPeriod;
        private int slowPeriod;
        private int signalPeriod;
    }

    @Data
    public static class Adx {
        private int period;
        private double threshold;
        private double strongThreshold;
    }

    @Data
    public static class Rsi {
        private int period;
        private double goldilocksMin;
        private double goldilocksMax;
        private double overbought;
        private double oversold;
    }

    @Data
    public static class Atr {
        private int period;
        private double minPercent;
        private double maxPercent;
        private double stopMultiplier;
        private double targetMultiplier;
    }

    @Data
    public static class Squeeze {
        private int bollingerPeriod;
        private double bollingerDeviation;
        private int keltnerPeriod;
        private double keltnerAtrMultiplier;
        private int minBars;
        private double tightThreshold;
    }

    @Data
    public static class Risk {
        private double dailyLossLimitPct;
        private int maxConsecutiveLosses;
        private double minEquity;
        private double maxPositionLossPct;
        private double slippagePct;
    }

    @Data
    public static class Scoring {
        private double vwapWeight;
        private double momentumWeight;
        private double trendWeight;
        private double rsiWeight;
        private double volatilityWeight;
        private double squeezeWeight;
        private int minEntryScore;
    }

    @Data
    public static class Sizing {
        private double multiplierA3;
        private double multiplierA2;
        private double multiplierA1;
        private double multiplierA0;
        private double multiplierB;
    }

    @Data
    public static class Exit {
        private double breakevenThreshold;
        private double trailingThreshold;
        private double trailingMultiplier;
        private int timeStopBars;
        private int maxBars;
    }

    @Data
    public static class Scanner {
        private boolean enabled = true;
        private int minScore = 60;
        private int maxCandidates = 10;
        private long interval = 300000;
    }
}