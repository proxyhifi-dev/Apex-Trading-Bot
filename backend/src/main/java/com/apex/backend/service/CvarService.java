package com.apex.backend.service;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CvarService {

    public double calculate(List<Double> returns, double confidence) {
        if (returns == null || returns.isEmpty()) {
            return 0.0;
        }
        List<Double> sorted = returns.stream().sorted().toList();
        int tailCount = (int) Math.floor((1.0 - confidence) * sorted.size());
        tailCount = Math.max(tailCount, 1);
        List<Double> tail = sorted.subList(0, tailCount);
        return tail.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }
}
