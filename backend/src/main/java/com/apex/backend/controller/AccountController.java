package com.apex.backend.controller;

import com.apex.backend.dto.AccountOverviewDTO;
import com.apex.backend.dto.AccountProfileDTO;
import com.apex.backend.dto.HoldingDTO;
import com.apex.backend.dto.UserProfileDTO;
import com.apex.backend.exception.ConflictException;
import com.apex.backend.model.PaperAccount;
import com.apex.backend.model.PaperPortfolioStats;
import com.apex.backend.exception.UnauthorizedException;
import com.apex.backend.model.TradingMode;
import com.apex.backend.security.UserPrincipal;
import com.apex.backend.service.FyersAuthService;
import com.apex.backend.service.FyersService;
import com.apex.backend.service.PaperTradingService;
import com.apex.backend.service.SettingsService;
import com.apex.backend.repository.UserRepository;
import com.apex.backend.util.MoneyUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {

    private final FyersAuthService fyersAuthService;
    private final FyersService fyersService;
    private final PaperTradingService paperTradingService;
    private final SettingsService settingsService;
    private final UserRepository userRepository;
    
    /**
     * Get user profile
     */
    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(
            @AuthenticationPrincipal UserPrincipal principal) throws Exception {
        log.info("Fetching user profile");
        Long userId = requireUserId(principal);
        TradingMode mode = settingsService.getTradingMode(userId);
        if (mode == TradingMode.PAPER) {
            return ResponseEntity.ok(AccountProfileDTO.builder()
                    .name(resolveUserName(userId))
                    .clientId("PAPER")
                    .connected(false)
                    .build());
        }
        String token = resolveFyersToken(userId);
        Map<String, Object> profile = fyersService.getProfile(token);
        log.info("Successfully retrieved profile");
        return ResponseEntity.ok(AccountProfileDTO.builder()
                .name(extractProfileName(profile))
                .clientId(extractProfileId(profile))
                .connected(resolveFyersConnected(userId))
                .build());
    }
    
    /**
     * Get account summary (PAPER or LIVE)
     */
    @GetMapping("/summary")
    public ResponseEntity<?> getSummary(
            @AuthenticationPrincipal UserPrincipal principal) throws Exception {
        Long userId = requireUserId(principal);
        TradingMode mode = settingsService.getTradingMode(userId);
        log.info("Fetching account summary for mode: {}", mode);

        if (mode == TradingMode.PAPER) {
            PaperPortfolioStats stats = paperTradingService.getStats(userId);
            PaperAccount account = paperTradingService.getAccount(userId);
            BigDecimal totalValue = MoneyUtils.add(
                    MoneyUtils.add(account.getCashBalance(), account.getReservedMargin()),
                    account.getUnrealizedPnl()
            );
            UserProfileDTO summary = UserProfileDTO.builder()
                    .name(resolveUserName(userId))
                    .availableFunds(account.getCashBalance())
                    .totalInvested(account.getReservedMargin())
                    .currentValue(totalValue)
                    .todaysPnl(stats.getNetPnl() != null ? stats.getNetPnl() : MoneyUtils.ZERO)
                    .holdings(new ArrayList<>())
                    .build();
            return ResponseEntity.ok(summary);
        }
        String token = resolveFyersToken(userId);
        return ResponseEntity.ok(fyersService.getFunds(token));
    }

    @GetMapping("/holdings")
    public ResponseEntity<?> getHoldings(@AuthenticationPrincipal UserPrincipal principal) throws Exception {
        Long userId = requireUserId(principal);
        TradingMode mode = settingsService.getTradingMode(userId);
        if (mode == TradingMode.PAPER) {
            List<com.apex.backend.model.PaperPosition> positions = paperTradingService.getOpenPositions(userId);
            List<HoldingDTO> holdings = positions.stream()
                    .map(this::toHoldingDto)
                    .toList();
            return ResponseEntity.ok(holdings);
        }
        String token = resolveFyersToken(userId);
        return ResponseEntity.ok(mapHoldings(fyersService.getHoldings(token)));
    }
    
    /**
     * Get capital information
     */
    @GetMapping("/capital")
    public ResponseEntity<?> getCapital(@AuthenticationPrincipal UserPrincipal principal) throws Exception {
        Long userId = requireUserId(principal);
        TradingMode mode = settingsService.getTradingMode(userId);
        if (mode == TradingMode.PAPER) {
            PaperAccount account = paperTradingService.getAccount(userId);
            return ResponseEntity.ok(new CapitalInfo(account.getStartingCapital(), account.getCashBalance(), account.getReservedMargin()));
        }
        String token = resolveFyersToken(userId);
        return ResponseEntity.ok(fyersService.getFunds(token));
    }

    @GetMapping("/overview")
    public ResponseEntity<?> getOverview(@AuthenticationPrincipal UserPrincipal principal) throws Exception {
        Long userId = requireUserId(principal);
        TradingMode mode = settingsService.getTradingMode(userId);
        if (mode == TradingMode.PAPER) {
            PaperAccount account = paperTradingService.getAccount(userId);
            BigDecimal pnl = MoneyUtils.add(account.getRealizedPnl(), account.getUnrealizedPnl());
            List<com.apex.backend.model.PaperPosition> openPositions = paperTradingService.getOpenPositions(userId);
            List<AccountOverviewDTO.PositionDTO> positions = openPositions.stream()
                    .map(this::toPositionDto)
                    .toList();
            return ResponseEntity.ok(AccountOverviewDTO.builder()
                    .mode("PAPER")
                    .profile(AccountOverviewDTO.ProfileDTO.builder()
                            .name(resolveUserName(userId))
                            .brokerId("PAPER")
                            .email(resolveUserEmail(userId))
                            .build())
                    .funds(AccountOverviewDTO.FundsDTO.builder()
                            .cash(account.getCashBalance())
                            .used(account.getReservedMargin())
                            .free(account.getCashBalance())
                            .totalPnl(pnl)
                            .dayPnl(pnl)
                            .build())
                    .holdings(Collections.emptyList())
                    .positions(positions)
                    .recentTrades(Collections.emptyList())
                    .lastUpdatedAt(account.getUpdatedAt())
                    .dataSource("PAPER")
                    .build());
        }

        String token = resolveFyersToken(userId);
        Map<String, Object> profile = fyersService.getProfile(token);
        Map<String, Object> funds = fyersService.getFunds(token);
        Map<String, Object> holdings = fyersService.getHoldings(token);
        Map<String, Object> positions = fyersService.getPositions(token);
        return ResponseEntity.ok(AccountOverviewDTO.builder()
                .mode("LIVE")
                .profile(AccountOverviewDTO.ProfileDTO.builder()
                        .name(extractProfileName(profile))
                        .brokerId(extractProfileId(profile))
                        .email(extractProfileEmail(profile))
                        .build())
                .funds(AccountOverviewDTO.FundsDTO.builder()
                        .cash(extractFundValue(funds, "cash"))
                        .used(extractFundValue(funds, "used"))
                        .free(extractFundValue(funds, "available"))
                        .totalPnl(extractFundValue(funds, "pnl"))
                        .dayPnl(extractFundValue(funds, "day_pnl"))
                        .build())
                .holdings(mapHoldings(holdings))
                .positions(mapPositions(positions))
                .recentTrades(Collections.emptyList())
                .lastUpdatedAt(java.time.LocalDateTime.now())
                .dataSource("FYERS")
                .build());
    }
    
    // ==================== INNER CLASSES ====================
    
    public static class CapitalInfo {
        public BigDecimal initialCapital;
        public BigDecimal availableCapital;
        public BigDecimal usedCapital;

        public CapitalInfo(BigDecimal initialCapital, BigDecimal availableCapital, BigDecimal usedCapital) {
            this.initialCapital = initialCapital;
            this.availableCapital = availableCapital;
            this.usedCapital = usedCapital;
        }
    }

    private String resolveFyersToken(Long userId) {
        String token = fyersAuthService.getFyersToken(userId);
        if (token == null || token.isBlank()) {
            throw new ConflictException("Fyers account not linked");
        }
        return token;
    }

    private Long requireUserId(UserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new UnauthorizedException("Missing authentication");
        }
        return principal.getUserId();
    }

    @SuppressWarnings("unchecked")
    private String extractProfileName(Map<String, Object> profile) {
        Object data = profile.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            Object name = dataMap.get("name");
            return name != null ? name.toString() : "Live Account";
        }
        return "Live Account";
    }

    @SuppressWarnings("unchecked")
    private String extractProfileId(Map<String, Object> profile) {
        Object data = profile.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            Object fyId = dataMap.get("fy_id");
            return fyId != null ? fyId.toString() : null;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private BigDecimal extractFundValue(Map<String, Object> funds, String key) {
        Object data = funds.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            Object value = dataMap.get(key);
            if (value instanceof Number number) {
                return MoneyUtils.bd(number.doubleValue());
            }
        }
        return MoneyUtils.ZERO;
    }

    private String extractProfileEmail(Map<String, Object> profile) {
        Object data = profile.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            Object email = dataMap.get("email_id");
            return email != null ? email.toString() : null;
        }
        return null;
    }

    private String resolveUserName(Long userId) {
        return userRepository.findById(userId)
                .map(user -> user.getUsername() != null ? user.getUsername() : user.getEmail())
                .orElse("Paper Trading Account");
    }

    private String resolveUserEmail(Long userId) {
        return userRepository.findById(userId)
                .map(user -> user.getEmail())
                .orElse(null);
    }

    private boolean resolveFyersConnected(Long userId) {
        return userRepository.findById(userId)
                .map(user -> Boolean.TRUE.equals(user.getFyersConnected()))
                .orElse(false);
    }

    @SuppressWarnings("unchecked")
    private List<HoldingDTO> mapHoldings(Map<String, Object> holdings) {
        Object data = holdings.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            Object holdingsList = dataMap.get("holdings");
            if (holdingsList instanceof List<?> list) {
                return list.stream()
                        .filter(Map.class::isInstance)
                        .map(item -> (Map<String, Object>) item)
                        .map(this::toHoldingDto)
                        .toList();
            }
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private List<AccountOverviewDTO.PositionDTO> mapPositions(Map<String, Object> positions) {
        Object data = positions.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            Object netPositions = dataMap.get("netPositions");
            if (netPositions instanceof List<?> list) {
                return list.stream()
                        .filter(Map.class::isInstance)
                        .map(item -> (Map<String, Object>) item)
                        .map(this::toPositionDto)
                        .toList();
            }
        }
        return Collections.emptyList();
    }

    private HoldingDTO toHoldingDto(Map<String, Object> data) {
        return HoldingDTO.builder()
                .symbol(stringValue(data.get("symbol")))
                .quantity(intValue(data.get("quantity")))
                .avgPrice(numberValue(data.get("cost_price")))
                .currentPrice(numberValue(data.get("ltp")))
                .pnl(numberValue(data.get("pl")))
                .pnlPercent(numberValue(data.get("pl_perc")))
                .build();
    }

    private AccountOverviewDTO.PositionDTO toPositionDto(Map<String, Object> data) {
        return AccountOverviewDTO.PositionDTO.builder()
                .symbol(stringValue(data.get("symbol")))
                .side(stringValue(data.get("side")))
                .quantity(intValue(data.get("qty")))
                .avgPrice(numberValue(data.get("avg_price")))
                .currentPrice(numberValue(data.get("ltp")))
                .pnl(numberValue(data.get("pl")))
                .pnlPercent(numberValue(data.get("pl_perc")))
                .build();
    }

    private AccountOverviewDTO.PositionDTO toPositionDto(com.apex.backend.model.PaperPosition position) {
        BigDecimal ltp = position.getLastPrice() != null ? position.getLastPrice() : position.getAveragePrice();
        BigDecimal pnl = position.getUnrealizedPnl() != null ? position.getUnrealizedPnl() : MoneyUtils.ZERO;
        BigDecimal denominator = MoneyUtils.multiply(position.getAveragePrice(), position.getQuantity());
        BigDecimal pnlPercent = BigDecimal.ZERO;
        if (denominator.compareTo(BigDecimal.ZERO) > 0) {
            pnlPercent = MoneyUtils.scale(pnl.divide(denominator, MoneyUtils.SCALE, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)));
        }
        return AccountOverviewDTO.PositionDTO.builder()
                .symbol(position.getSymbol())
                .side(position.getSide())
                .quantity(position.getQuantity())
                .avgPrice(position.getAveragePrice())
                .currentPrice(ltp)
                .pnl(pnl)
                .pnlPercent(pnlPercent)
                .build();
    }

    private HoldingDTO toHoldingDto(com.apex.backend.model.PaperPosition position) {
        BigDecimal ltp = position.getLastPrice() != null ? position.getLastPrice() : position.getAveragePrice();
        BigDecimal pnl = position.getUnrealizedPnl() != null ? position.getUnrealizedPnl() : MoneyUtils.ZERO;
        BigDecimal denominator = MoneyUtils.multiply(position.getAveragePrice(), position.getQuantity());
        BigDecimal pnlPercent = BigDecimal.ZERO;
        if (denominator.compareTo(BigDecimal.ZERO) > 0) {
            pnlPercent = MoneyUtils.scale(pnl.divide(denominator, MoneyUtils.SCALE, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)));
        }
        return HoldingDTO.builder()
                .symbol(position.getSymbol())
                .quantity(position.getQuantity())
                .avgPrice(position.getAveragePrice())
                .currentPrice(ltp)
                .pnl(pnl)
                .pnlPercent(pnlPercent)
                .build();
    }

    private BigDecimal numberValue(Object value) {
        if (value instanceof Number number) {
            return MoneyUtils.bd(number.doubleValue());
        }
        return MoneyUtils.ZERO;
    }

    private String stringValue(Object value) {
        return value != null ? value.toString() : null;
    }

    private Integer intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }
}
