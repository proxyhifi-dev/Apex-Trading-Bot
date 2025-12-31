# Correlation Analysis API Documentation

## Overview
The Correlation API provides statistical analysis of portfolio asset correlations to support risk management and portfolio optimization decisions. It calculates Pearson correlation coefficients between trading positions.

## Endpoints

### GET /api/risk/correlation-matrix
Retrieve the correlation matrix for all portfolio positions.

**Description:**
Calculates the Pearson correlation coefficients between all pairs of stocks in the portfolio. Returns a 2D matrix where each cell [i][j] represents the correlation between stock i and stock j.

**HTTP Method:** GET
**Base URL:** `http://localhost:8080`
**Endpoint:** `/api/risk/correlation-matrix`

**Response Status Codes:**
- `200 OK` - Successfully retrieved correlation matrix
- `500 INTERNAL_SERVER_ERROR` - Server error during calculation

**Response Body:**
```json
{
  "stocks": ["TCS", "RELIANCE", "INFY"],
  "matrix": [
    [1.0, 0.28, 0.65],
    [0.28, 1.0, 0.38],
    [0.65, 0.38, 1.0]
  ]
}
```

**Response Fields:**
- `stocks` (Array[String]): List of stock symbols in the same order as matrix rows/columns
- `matrix` (Array[Array[Number]]): 2D correlation matrix with values between -1 and 1
  - 1.0 = Perfect positive correlation (assets move in lockstep)
  - 0.0 = No correlation (assets move independently)
  - -1.0 = Perfect negative correlation (assets move opposite)

## Data Flow

### Frontend Integration
1. **Angular Component:** `CorrelationHeatmapComponent`
2. **HTTP Call:** GET to `/api/risk/correlation-matrix`
3. **Visualization:** 2D heatmap with color-coded correlation values
   - Red (0.7-1.0): High correlation
   - Orange (0.4-0.7): Medium correlation
   - Blue (0.0-0.4): Low correlation

### Backend Processing
1. **Controller:** `RiskController.getCorrelationMatrix()`
2. **Service:** `CorrelationService.buildCorrelationMatrix()`
3. **Calculation:** Pearson correlation formula applied to price series

## Mathematical Formula

Pearson Correlation Coefficient:
```
r(X,Y) = Cov(X,Y) / (σx * σy)

Where:
  Cov(X,Y) = covariance of X and Y
  σx, σy = standard deviations of X and Y
```

## Implementation Details

### CorrelationService
**File:** `backend/src/main/java/com/apex/backend/service/CorrelationService.java`

**Key Methods:**
- `calculateCorrelation(List<Double> series1, List<Double> series2)`: Calculates correlation between two price series
- `buildCorrelationMatrix(Map<String, List<Double>> portfolioData)`: Builds complete correlation matrix for portfolio

### Error Handling
- Empty series: Returns 0 correlation
- Zero variance: Returns 0 correlation
- Missing data: Uses available data points

## Usage Example

### cURL Request
```bash
curl -X GET "http://localhost:8080/api/risk/correlation-matrix" \
  -H "Accept: application/json"
```

### TypeScript/Angular
```typescript
this.http.get('/api/risk/correlation-matrix')
  .subscribe(data => {
    console.log('Stocks:', data.stocks);
    console.log('Correlation Matrix:', data.matrix);
  });
```

## Future Enhancements

1. **Real Portfolio Integration**
   - Replace mock data with actual position data from portfolio service
   - Real-time correlation updates as positions change

2. **Advanced Statistics**
   - Covariance matrix
   - Portfolio diversification metrics
   - Risk contribution by asset

3. **Performance Optimization**
   - Cache correlation calculations
   - Batch processing for large portfolios
   - Asynchronous calculation with WebSocket updates

4. **Visualization Features**
   - Hierarchical clustering dendrograms
   - Interactive correlation brushing
   - Historical correlation trends

## Dependencies

- **Frontend:** Angular 17+, CommonModule
- **Backend:** Spring Boot, Lombok, Java 11+

## Related Files

- Frontend: `frontend/src/app/features/risk/components/correlation-heatmap.component.ts`
- Backend Service: `backend/src/main/java/com/apex/backend/service/CorrelationService.java`
- Controller: `backend/src/main/java/com/apex/backend/controller/RiskController.java`

## Testing

Current implementation uses mock portfolio data for testing:
- TCS: 5-point price series
- RELIANCE: 5-point price series  
- INFY: 5-point price series

TODO: Integrate with real portfolio position data and implement unit tests
