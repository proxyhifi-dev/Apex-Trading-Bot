package com.apex.backend.controller;

import com.apex.backend.dto.ClosePositionRequest;
import com.apex.backend.dto.OrderModifyRequest;
import com.apex.backend.dto.OrderResponse;
import com.apex.backend.dto.OrderValidationResult;
import com.apex.backend.dto.PlaceOrderRequest;
import com.apex.backend.exception.UnauthorizedException;
import com.apex.backend.security.UserPrincipal;
import com.apex.backend.service.OrderExecutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Orders")
public class OrderExecutionController {

    private final OrderExecutionService orderExecutionService;

    @PostMapping("/orders")
    @Operation(summary = "Place order")
    public ResponseEntity<OrderResponse> placeOrder(@AuthenticationPrincipal UserPrincipal principal,
                                                    @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                    @Valid @RequestBody PlaceOrderRequest request) {
        Long userId = requireUserId(principal);
        log.info("Placing order for user {} symbol {}", userId, request.getSymbol());
        if ((request.getClientOrderId() == null || request.getClientOrderId().isBlank())
                && idempotencyKey != null && !idempotencyKey.isBlank()) {
            request.setClientOrderId(idempotencyKey);
        }
        return ResponseEntity.ok(orderExecutionService.placeOrder(userId, request));
    }

    @PutMapping("/orders/{orderId}")
    @Operation(summary = "Modify order")
    public ResponseEntity<OrderResponse> modifyOrder(@AuthenticationPrincipal UserPrincipal principal,
                                                     @PathVariable String orderId,
                                                     @Valid @RequestBody OrderModifyRequest request) {
        Long userId = requireUserId(principal);
        return ResponseEntity.ok(orderExecutionService.modifyOrder(userId, orderId, request));
    }

    @DeleteMapping("/orders/{orderId}")
    @Operation(summary = "Cancel order")
    public ResponseEntity<OrderResponse> cancelOrder(@AuthenticationPrincipal UserPrincipal principal,
                                                     @PathVariable String orderId) {
        Long userId = requireUserId(principal);
        return ResponseEntity.ok(orderExecutionService.cancelOrder(userId, orderId));
    }

    @PostMapping("/positions/{symbol}/close")
    @Operation(summary = "Close position", tags = {"Positions"})
    public ResponseEntity<OrderResponse> closePosition(@AuthenticationPrincipal UserPrincipal principal,
                                                       @PathVariable String symbol,
                                                       @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                       @Valid @RequestBody(required = false) ClosePositionRequest request) {
        Long userId = requireUserId(principal);
        Integer qty = request != null ? request.getQty() : null;
        String clientOrderId = request != null ? request.getClientOrderId() : null;
        if ((clientOrderId == null || clientOrderId.isBlank()) && idempotencyKey != null && !idempotencyKey.isBlank()) {
            clientOrderId = idempotencyKey;
        }
        return ResponseEntity.ok(orderExecutionService.closePosition(userId, symbol, qty, clientOrderId));
    }

    @PostMapping("/orders/validate")
    @Operation(summary = "Validate order (dry run)")
    public ResponseEntity<OrderValidationResult> validate(@AuthenticationPrincipal UserPrincipal principal,
                                                          @Valid @RequestBody PlaceOrderRequest request) {
        Long userId = requireUserId(principal);
        return ResponseEntity.ok(orderExecutionService.validate(userId, request));
    }

    private Long requireUserId(UserPrincipal principal) {
        if (principal == null || principal.getUserId() == null) {
            throw new UnauthorizedException("Missing authentication");
        }
        return principal.getUserId();
    }
}
