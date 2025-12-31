package com.apex.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.*;

@Slf4j
@Service
public class CorrelationService {

    /**
     * Calculate correlation between two price series
     * Returns correlation coefficient between -1 and 1
     * -1: Perfect negative correlation
     *  0: No correlation
     *  1: Perfect positive correlation
     */
    public double calculateCorrelation(List<Double> series1, List<Double> series2) {
        if (series1.size() != series2.size() || series1.isEmpty()) {
            return 0;
        }

        double mean1 = series1.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double mean2 = series2.stream().mapToDouble(Double::doubleValue).average().orElse(0);

        double covariance = 0;
        double variance1 = 0;
        double variance2 = 0;

        for (int i = 0; i < series1.size(); i++) {
            double diff1 = series1.get(i) - mean1;
            double diff2 = series2.get(i) - mean2;
            covariance += diff1 * diff2;
            variance1 += diff1 * diff1;
            variance2 += diff2 * diff2;
        }

        double stdDev1 = Math.sqrt(variance1 / series1.size());
        double stdDev2 = Math.sqrt(variance2 / series2.size());

        if (stdDev1 == 0 || stdDev2 == 0) {
            return 0;
        }

        return (covariance / series1.size()) / (stdDev1 * stdDev2);
    }

    /**
     * Calculate correlation matrix for a portfolio of stocks
     * @param portfolioData Map of stock symbol to price series
     * @return 2D array representing correlation matrix
     */
    public CorrelationMatrix buildCorrelationMatrix(Map<String, List<Double>> portfolioData) {
        List<String> stocks = new ArrayList<>(portfolioData.keySet());
        int size = stocks.size();
        double[][] matrix = new double[size][size];

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (i == j) {
                    matrix[i][j] = 1.0; // Perfect correlation with itself
                } else {
                    matrix[i][j] = calculateCorrelation(
                        portfolioData.get(stocks.get(i)),
                        portfolioData.get(stocks.get(j))
                    );
                }
            }
        }

        return new CorrelationMatrix(stocks, matrix);
    }

    /**
     * DTO for correlation matrix response
     */
    public static class CorrelationMatrix {
        public List<String> stocks;
        public double[][] matrix;

        public CorrelationMatrix(List<String> stocks, double[][] matrix) {
            this.stocks = stocks;
            this.matrix = matrix;
        }
    }
}
