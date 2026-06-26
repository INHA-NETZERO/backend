package com.netzero.forecast.domain;

import com.netzero.common.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "demand_forecast")
public class DemandForecast extends BaseEntity {

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    @Column(name = "p10", precision = 12, scale = 3)
    private BigDecimal p10;

    @Column(name = "p50", precision = 12, scale = 3)
    private BigDecimal p50;

    @Column(name = "p90", precision = 12, scale = 3)
    private BigDecimal p90;

    @Column(name = "predicted_quantity", precision = 12, scale = 3)
    private BigDecimal predictedQuantity;

    @Column(name = "model_version", length = 40)
    private String modelVersion;

    @Column(name = "features", columnDefinition = "JSON")
    @JdbcTypeCode(SqlTypes.JSON)
    private String features;

    public DemandForecast() {}

    public Long getStoreId() { return storeId; }
    public void setStoreId(Long storeId) { this.storeId = storeId; }

    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }

    public LocalDate getTargetDate() { return targetDate; }
    public void setTargetDate(LocalDate targetDate) { this.targetDate = targetDate; }

    public BigDecimal getP10() { return p10; }
    public void setP10(BigDecimal p10) { this.p10 = p10; }

    public BigDecimal getP50() { return p50; }
    public void setP50(BigDecimal p50) { this.p50 = p50; }

    public BigDecimal getP90() { return p90; }
    public void setP90(BigDecimal p90) { this.p90 = p90; }

    public BigDecimal getPredictedQuantity() { return predictedQuantity; }
    public void setPredictedQuantity(BigDecimal predictedQuantity) { this.predictedQuantity = predictedQuantity; }

    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }

    public String getFeatures() { return features; }
    public void setFeatures(String features) { this.features = features; }
}
