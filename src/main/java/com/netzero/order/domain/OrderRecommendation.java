package com.netzero.order.domain;

import com.netzero.common.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "order_recommendation")
public class OrderRecommendation extends BaseEntity {

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    @Column(name = "recommended_quantity", precision = 12, scale = 3)
    private BigDecimal recommendedQuantity;

    @Column(name = "actual_quantity", precision = 12, scale = 3)
    private BigDecimal actualQuantity;

    @Column(name = "actual_updated_at")
    private LocalDateTime actualUpdatedAt;

    @Column(name = "optimal_stock_quantity", precision = 12, scale = 3)
    private BigDecimal optimalStockQuantity;

    @Column(name = "baseline_quantity", precision = 12, scale = 3)
    private BigDecimal baselineQuantity;

    @Column(name = "critical_ratio", precision = 6, scale = 4)
    private BigDecimal criticalRatio;

    @Column(name = "expected_waste_avoided_kg", precision = 12, scale = 3)
    private BigDecimal expectedWasteAvoidedKg;

    @Column(name = "rationale", columnDefinition = "JSON")
    @JdbcTypeCode(SqlTypes.JSON)
    private String rationale;

    public OrderRecommendation() {}

    public Long getStoreId() { return storeId; }
    public void setStoreId(Long storeId) { this.storeId = storeId; }

    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }

    public LocalDate getTargetDate() { return targetDate; }
    public void setTargetDate(LocalDate targetDate) { this.targetDate = targetDate; }

    public BigDecimal getRecommendedQuantity() { return recommendedQuantity; }
    public void setRecommendedQuantity(BigDecimal recommendedQuantity) { this.recommendedQuantity = recommendedQuantity; }

    public BigDecimal getActualQuantity() { return actualQuantity; }
    public void setActualQuantity(BigDecimal actualQuantity) { this.actualQuantity = actualQuantity; }

    public LocalDateTime getActualUpdatedAt() { return actualUpdatedAt; }
    public void setActualUpdatedAt(LocalDateTime actualUpdatedAt) { this.actualUpdatedAt = actualUpdatedAt; }

    public BigDecimal getOptimalStockQuantity() { return optimalStockQuantity; }
    public void setOptimalStockQuantity(BigDecimal optimalStockQuantity) { this.optimalStockQuantity = optimalStockQuantity; }

    public BigDecimal getBaselineQuantity() { return baselineQuantity; }
    public void setBaselineQuantity(BigDecimal baselineQuantity) { this.baselineQuantity = baselineQuantity; }

    public BigDecimal getCriticalRatio() { return criticalRatio; }
    public void setCriticalRatio(BigDecimal criticalRatio) { this.criticalRatio = criticalRatio; }

    public BigDecimal getExpectedWasteAvoidedKg() { return expectedWasteAvoidedKg; }
    public void setExpectedWasteAvoidedKg(BigDecimal expectedWasteAvoidedKg) { this.expectedWasteAvoidedKg = expectedWasteAvoidedKg; }

    public String getRationale() { return rationale; }
    public void setRationale(String rationale) { this.rationale = rationale; }
}
