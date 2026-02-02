package com.apex.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class BrokerUnreachableListener {

    private final EmergencyPanicService emergencyPanicService;

    @EventListener(BrokerUnreachableEvent.class)
    public void onBrokerUnreachable(BrokerUnreachableEvent event) {
        log.error("Broker unreachable broker={} reason={} occurredAt={}",
                event.broker(), event.reason(), event.occurredAt());
        emergencyPanicService.triggerGlobalEmergency("BROKER_UNREACHABLE:" + event.broker());
    }
}
