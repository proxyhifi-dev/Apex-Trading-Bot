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

    private Trading trading = new Trading();
    private Strategy strategy = new Strategy();
    private Risk risk = new Risk();
    private Scanner scanner = new Scanner();

    @Data
    public static class Trading {
        // ✅ NEW: Global Paper Mode Toggle (Default: true)
        private boolean paperMode = true;
        private boolean paperSignalOrdersEnabled = false;
        private double capital;
        private Long ownerUserId;
        private Universe universe;

        @Data
        public static class Universe {
            private List<String> symbols;
        }
    }

    @Data
    public static class Strategy {
        private String name;
        private int minCandleCount = 50;
        private int rsiPeriod = 14;
        private double rsiNeutral = 50.0;
        private double rsiGoldilocksMin = 40.0;
        private double rsiGoldilocksMax = 70.0;
        private double momentumWeight = 20.0;
        private double trendWeight = 20.0;
        private double rsiWeight = 20.0;
        private double volatilityWeight = 20.0;
        private double squeezeWeight = 20.0;
        private int macdFastPeriod = 12;
        private int macdSlowPeriod = 26;
        private int macdSignalPeriod = 9;
        private double macdMinMomentumScore = 4.0;
        private double macdMomentumScale = 11.0;
        private double macdHistogramMin = 0.0;
        private int adxPeriod = 14;
        private double adxThreshold = 20.0;
        private double adxStrongThreshold = 25.0;
        private int bollingerPeriod = 20;
        private double bollingerStdDev = 2.0;
        private double atrMinPercent = 0.3;
        private double atrMaxPercent = 6.0;
        private double initialCapital = 100000.0;
        private int minEntryScore = 65;
        private int gradeAaaThreshold = 90;
        private int gradeAaThreshold = 85;
        private int gradeAThreshold = 80;
    }

    @Data
    public static class Risk {
        private double maxPositionLossPct = 0.01;
        private double dailyLossLimitPct = 0.02;
        private int maxConsecutiveLosses = 3;
        private double minEquity = 50000.0;
        // ✅ Slippage for Paper Mode (0.1%)
        private double slippagePct = 0.001;

        private double maxSingleTradeCapitalPct = 0.35;
        private int maxOpenPositions = 3;
        private int maxSectorPositions = 2;
        private double maxCorrelation = 0.7;
        private double targetMultiplier = 3.0;
        private double breakevenMoveR = 1.0;
        private double breakevenOffsetR = 0.1;
        private double trailingStartR = 2.0;
        private double trailingAtrMultiplier = 1.5;
        private double momentumWeaknessTightenR = 0.5;
        private double momentumWeaknessStopOffsetR = 0.25;

        // Circuit Breakers
        private double weeklyLossLimitPct = 0.10;
        private double monthlyLossLimitPct = 0.15;
        private double maxDrawdownPct = 0.20;
        private int consecutivePauseHours = 24;
    }

    @Data
    public static class Scanner {
        private boolean enabled = true;
        private boolean schedulerEnabled = false;
        private Mode mode = Mode.MANUAL;
        private int interval = 60;
        private int minScore = 70;
        private int maxCandidates = 5;
        private boolean requireManualApproval = false;
        private String defaultTimeframe = "5";
        private String defaultRegime = "AUTO";
        private String marketOpen = "09:15";
        private String marketClose = "15:30";
        private Universes universes = new Universes();

        public enum Mode {
            MANUAL,
            SCHEDULED
        }

        @Data
        public static class Universes {
            private List<String> nifty50 = new ArrayList<>();
            private List<String> nifty200 = new ArrayList<>();
        }
    }
}
