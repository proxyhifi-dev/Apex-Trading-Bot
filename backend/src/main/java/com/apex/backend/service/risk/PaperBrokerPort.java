package com.apex.backend.service.risk;

import com.apex.backend.model.PaperOrder;
import com.apex.backend.model.PaperPosition;
import com.apex.backend.repository.PaperOrderRepository;
import com.apex.backend.repository.PaperPositionRepository;
import com.apex.backend.util.MoneyUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaperBrokerPort implements BrokerPort {

    private final PaperOrderRepository paperOrderRepository;
    private final PaperPositionRepository paperPositionRepository;

    @Override
    public List<BrokerOrder> openOrders(Long userId) {
        List<BrokerOrder> orders = new ArrayList<>();
        for (PaperOrder order : paperOrderRepository.findByUserId(userId)) {
            if (isOpen(order.getStatus())) {
                orders.add(new BrokerOrder(order.getOrderId(), order.getSymbol(), order.getStatus(), order.getQuantity(), order.getPrice()));
            }
        }
        return orders;
    }

    @Override
    public List<BrokerPosition> openPositions(Long userId) {
        List<BrokerPosition> positions = new ArrayList<>();
        for (PaperPosition position : paperPositionRepository.findByUserIdAndStatus(userId, "OPEN")) {
            BigDecimal avgPrice = position.getAveragePrice() != null ? position.getAveragePrice() : MoneyUtils.ZERO;
            positions.add(new BrokerPosition(position.getSymbol(), position.getQuantity(), avgPrice));
        }
        return positions;
    }

    @Override
    public void cancelOrder(Long userId, String brokerOrderId) {
        paperOrderRepository.findByUserId(userId).stream()
                .filter(order -> brokerOrderId.equals(order.getOrderId()))
                .forEach(order -> {
                    order.setStatus("CANCELLED");
                    paperOrderRepository.save(order);
                });
    }

    private boolean isOpen(String status) {
        if (status == null) {
            return true;
        }
        String normalized = status.toUpperCase();
        return !("FILLED".equals(normalized) || "CANCELLED".equals(normalized) || "REJECTED".equals(normalized));
    }
}
