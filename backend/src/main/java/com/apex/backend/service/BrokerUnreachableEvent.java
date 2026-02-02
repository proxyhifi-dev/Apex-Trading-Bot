package com.apex.backend.service;

import java.time.Instant;

public record BrokerUnreachableEvent(String broker, String reason, Instant occurredAt) {}
