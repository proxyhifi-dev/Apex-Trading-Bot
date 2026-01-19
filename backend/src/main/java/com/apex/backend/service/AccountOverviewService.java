package com.apex.backend.service;

import com.apex.backend.dto.AccountOverviewDTO;
import com.apex.backend.dto.HoldingDTO;
import com.apex.backend.exception.NotFoundException;
import com.apex.backend.model.PaperAccount;
import com.apex.backend.model.TradingMode;
import com.apex.backend.repository.UserRepository;
import com.apex.backend.util.MoneyUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AccountOverviewService {

    private final FyersService fyersService;
    private final PaperTradingService paperTradingService;
    private final SettingsService settingsService;
    private final UserRepository userRepository;

    public AccountOverviewDTO buildOverview(Long userId) throws Exception {
        TradingMode mode = settingsService.getTradingMode(userId);
        if (mode == TradingMode.PAPER) {
            PaperAccount account = paperTradingService.getAccount(userId);
            List<com.apex.backend.model.PaperPosition> openPositions = paperTradingService.getOpenPositions(userId);
            BigDecimal positionValue = paperTradingService.getOpenPositionsMarketValue(userId);
            BigDecimal unrealizedPnl = paperTradingService.getOpenPositionsUnrealizedPnl(userId);
            BigDecimal pnl = MoneyUtils.add(account.getRealizedPnl(), unrealizedPnl);
            List<AccountOverviewDTO.PositionDTO> positions = openPositions.stream()
                    .map(this::toPositionDto)
                    .toList();
            return AccountOverviewDTO.builder()
                    .mode("PAPER")
                    .profile(AccountOverviewDTO.ProfileDTO.builder()
                            .name(resolveUserName(userId))
                            .brokerId("PAPER")
                            .email(resolveUserEmail(userId))
                            .build())
                    .funds(AccountOverviewDTO.FundsDTO.builder()
                            .cash(account.getCashBalance())
                            .used(positionValue)
                            .free(account.getCashBalance())
                            .totalPnl(pnl)
                            .dayPnl(pnl)
                            .build())
                    .holdings(Collections.emptyList())
                    .positions(positions)
                    .recentTrades(Collections.emptyList())
                    .lastUpdatedAt(account.getUpdatedAt())
                    .dataSource("PAPER")
                    .build();
        }

        Map<String, Object> profile = fyersService.getProfileForUser(userId);
        Map<String, Object> funds = fyersService.getFundsForUser(userId);
        Map<String, Object> holdings = fyersService.getHoldingsForUser(userId);
        Map<String, Object> positions = fyersService.getPositionsForUser(userId);
        return AccountOverviewDTO.builder()
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
                .build();
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

    private String extractProfileEmail(Map<String, Object> profile) {
        Object data = profile.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            Object email = dataMap.get("email_id");
            return email != null ? email.toString() : null;
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

    private String resolveUserName(Long userId) {
        return userRepository.findById(userId)
                .map(user -> user.getUsername() != null ? user.getUsername() : user.getEmail())
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private String resolveUserEmail(Long userId) {
        return userRepository.findById(userId)
                .map(user -> user.getEmail())
                .orElse(null);
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
