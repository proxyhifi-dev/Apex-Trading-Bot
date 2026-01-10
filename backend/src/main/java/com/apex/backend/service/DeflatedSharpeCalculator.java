package com.apex.backend.service;

import org.springframework.stereotype.Service;

@Service
public class DeflatedSharpeCalculator {

    public double calculate(double sharpe, int trades, int numTrials) {
        if (trades <= 1 || numTrials <= 1) {
            return sharpe;
        }
        double penalty = Math.sqrt(2.0 * Math.log(numTrials) / (trades - 1));
        return sharpe - penalty;
    }
}
