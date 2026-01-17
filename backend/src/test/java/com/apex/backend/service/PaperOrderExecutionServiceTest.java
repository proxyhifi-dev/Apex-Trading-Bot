package com.apex.backend.service;

import com.apex.backend.dto.PlaceOrderRequest;
import com.apex.backend.model.PaperAccount;
import com.apex.backend.model.PaperOrder;
import com.apex.backend.repository.PaperAccountRepository;
import com.apex.backend.repository.PaperOrderRepository;
import com.apex.backend.repository.PaperPositionRepository;
import com.apex.backend.util.MoneyUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest
class PaperOrderExecutionServiceTest {

    @Autowired
    private PaperOrderExecutionService paperOrderExecutionService;

    @Autowired
    private PaperAccountRepository paperAccountRepository;

    @Autowired
    private PaperOrderRepository paperOrderRepository;

    @Autowired
    private PaperPositionRepository paperPositionRepository;

    @MockBean
    private FyersService fyersService;

    @MockBean
    private BroadcastService broadcastService;

    private final Long userId = 101L;

    @BeforeEach
    void setup() {
        paperOrderRepository.deleteAll();
        paperPositionRepository.deleteAll();
        paperAccountRepository.deleteAll();
        paperAccountRepository.save(PaperAccount.builder()
                .userId(userId)
                .startingCapital(MoneyUtils.bd(100000))
                .cashBalance(MoneyUtils.bd(100000))
                .reservedMargin(MoneyUtils.ZERO)
                .realizedPnl(MoneyUtils.ZERO)
                .unrealizedPnl(MoneyUtils.ZERO)
                .updatedAt(java.time.LocalDateTime.now())
                .build());
    }

    @Test
    void marketOrderFillsImmediately() {
        when(fyersService.getLTP("NSE:INFY-EQ")).thenReturn(100.0);
        PlaceOrderRequest request = PlaceOrderRequest.builder()
                .exchange("NSE")
                .symbol("NSE:INFY-EQ")
                .side(PlaceOrderRequest.OrderSide.BUY)
                .qty(10)
                .orderType(PlaceOrderRequest.OrderType.MARKET)
                .productType(PlaceOrderRequest.ProductType.INTRADAY)
                .validity(PlaceOrderRequest.Validity.DAY)
                .build();

        PaperOrder order = paperOrderExecutionService.placeOrder(userId, request);

        assertThat(order.getStatus()).isEqualTo("FILLED");
        assertThat(paperPositionRepository.findByUserIdAndStatus(userId, "OPEN")).hasSize(1);
    }

    @Test
    void limitOrderRemainsOpenWhenNotTriggered() {
        when(fyersService.getLTP("NSE:INFY-EQ")).thenReturn(100.0);
        PlaceOrderRequest request = PlaceOrderRequest.builder()
                .exchange("NSE")
                .symbol("NSE:INFY-EQ")
                .side(PlaceOrderRequest.OrderSide.BUY)
                .qty(10)
                .orderType(PlaceOrderRequest.OrderType.LIMIT)
                .productType(PlaceOrderRequest.ProductType.INTRADAY)
                .price(BigDecimal.valueOf(95))
                .validity(PlaceOrderRequest.Validity.DAY)
                .build();

        PaperOrder order = paperOrderExecutionService.placeOrder(userId, request);

        assertThat(order.getStatus()).isEqualTo("OPEN");
    }

    @Test
    void slmOrderFillsImmediately() {
        when(fyersService.getLTP("NSE:INFY-EQ")).thenReturn(100.0);
        PlaceOrderRequest request = PlaceOrderRequest.builder()
                .exchange("NSE")
                .symbol("NSE:INFY-EQ")
                .side(PlaceOrderRequest.OrderSide.BUY)
                .qty(5)
                .orderType(PlaceOrderRequest.OrderType.SL_M)
                .productType(PlaceOrderRequest.ProductType.INTRADAY)
                .triggerPrice(BigDecimal.valueOf(98))
                .validity(PlaceOrderRequest.Validity.DAY)
                .build();

        PaperOrder order = paperOrderExecutionService.placeOrder(userId, request);

        assertThat(order.getStatus()).isEqualTo("FILLED");
    }

    @Test
    void closePositionUpdatesRealizedPnl() {
        when(fyersService.getLTP("NSE:INFY-EQ")).thenReturn(100.0);
        PlaceOrderRequest request = PlaceOrderRequest.builder()
                .exchange("NSE")
                .symbol("NSE:INFY-EQ")
                .side(PlaceOrderRequest.OrderSide.BUY)
                .qty(10)
                .orderType(PlaceOrderRequest.OrderType.MARKET)
                .productType(PlaceOrderRequest.ProductType.INTRADAY)
                .validity(PlaceOrderRequest.Validity.DAY)
                .build();
        paperOrderExecutionService.placeOrder(userId, request);

        when(fyersService.getLTP("NSE:INFY-EQ")).thenReturn(110.0);
        paperOrderExecutionService.closePosition(userId, "NSE:INFY-EQ", 10);

        PaperAccount account = paperAccountRepository.findByUserId(userId).orElseThrow();
        assertThat(account.getRealizedPnl()).isEqualByComparingTo(MoneyUtils.bd(100));
    }
}
