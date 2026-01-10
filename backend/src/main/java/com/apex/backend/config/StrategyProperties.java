package com.apex.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "strategy")
@Data
public class StrategyProperties {

    private Macd macd = new Macd();
    private Adx adx = new Adx();
    private Rsi rsi = new Rsi();
    private Atr atr = new Atr();
    private Bollinger bollinger = new Bollinger();
    private Keltner keltner = new Keltner();
    private Squeeze squeeze = new Squeeze();
    private Exit exit = new Exit();
    private Sizing sizing = new Sizing();
    private Circuit circuit = new Circuit();
    private Scoring scoring = new Scoring();
    private Scanner scanner = new Scanner();
    private Health health = new Health();

    @Data
    public static class Macd {
        private int fastPeriod = 12;
        private int slowPeriod = 26;
        private int signalPeriod = 9;
        private double minMomentumScore = 6.0;
    }

    @Data
    public static class Adx {
        private int period = 14;
        private double threshold = 25.0;
        private double strong = 30.0;
    }

    @Data
    public static class Rsi {
        private int period = 14;
        private double goldilocksMin = 45.0;
        private double goldilocksMax = 65.0;
        private double overbought = 70.0;
        private double oversold = 30.0;
    }

    @Data
    public static class Atr {
        private int period = 14;
        private double minPercent = 0.5;
        private double maxPercent = 5.0;
        private double stopMultiplier = 1.5;
        private double targetMultiplier = 3.0;
    }

    @Data
    public static class Bollinger {
        private int period = 20;
        private double deviation = 2.0;
    }

    @Data
    public static class Keltner {
        private int period = 20;
        private double atrMultiplier = 1.5;
    }

    @Data
    public static class Squeeze {
        private int minBars = 5;
        private double tightThreshold = 0.8;
    }

    @Data
    public static class Exit {
        private double breakevenThreshold = 0.5;
        private double trailingThreshold = 1.5;
        private double trailingMultiplier = 1.5;
        private int timeStopBars = 20;
        private int maxBars = 50;
    }

    @Data
    public static class Sizing {
        private double baseRisk = 0.01;
        private double multiplierAaa = 2.0;
        private double multiplierAa = 1.5;
        private double multiplierA = 1.0;
    }

    @Data
    public static class Circuit {
        private double dailyLossLimit = 0.02;
        private double weeklyLossLimit = 0.10;
        private double monthlyLossLimit = 0.15;
        private int maxConsecutiveLosses = 3;
        private boolean autoPause = true;
    }

    @Data
    public static class Scoring {
        private double momentumWeight = 20.0;
        private double trendWeight = 20.0;
        private double rsiWeight = 20.0;
        private double volatilityWeight = 20.0;
        private double squeezeWeight = 20.0;
    }

    @Data
    public static class Scanner {
        private int maxCandidates = 5;
        private boolean requireManualApproval = false;
    }

    @Data
    public static class Health {
        private int rollingTrades = 30;
        private double minExpectancy = 0.1;
        private double minSharpe = 0.5;
        private double maxDrawdownPct = 0.1;
        private double maxConsecutiveLossProbability = 0.2;
    }
}
