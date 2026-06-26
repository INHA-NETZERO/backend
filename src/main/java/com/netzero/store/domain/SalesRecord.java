package com.netzero.store.domain;

import com.netzero.common.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "sales_record")
public class SalesRecord extends BaseEntity {

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "day_of_week")
    private String dayOfWeek;

    private String weather;

    @Column(name = "avg_temp")
    private BigDecimal avgTemp;

    @Column(name = "precipitation_mm")
    private BigDecimal precipitationMm;

    private String event;

    @Column(name = "new_menu")
    private String newMenu;

    private String category;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "quantity_sold", nullable = false)
    private BigDecimal quantitySold;

    @Column(name = "scenario_note")
    private String scenarioNote;

    protected SalesRecord() {}

    public SalesRecord(Long storeId, LocalDate businessDate, String dayOfWeek, String weather,
                       BigDecimal avgTemp, BigDecimal precipitationMm, String event, String newMenu,
                       String category, Long itemId, BigDecimal quantitySold, String scenarioNote) {
        this.storeId = storeId;
        this.businessDate = businessDate;
        this.dayOfWeek = dayOfWeek;
        this.weather = weather;
        this.avgTemp = avgTemp;
        this.precipitationMm = precipitationMm;
        this.event = event;
        this.newMenu = newMenu;
        this.category = category;
        this.itemId = itemId;
        this.quantitySold = quantitySold;
        this.scenarioNote = scenarioNote;
    }

    public Long getStoreId() { return storeId; }
    public LocalDate getBusinessDate() { return businessDate; }
    public String getDayOfWeek() { return dayOfWeek; }
    public String getWeather() { return weather; }
    public BigDecimal getAvgTemp() { return avgTemp; }
    public BigDecimal getPrecipitationMm() { return precipitationMm; }
    public String getEvent() { return event; }
    public String getNewMenu() { return newMenu; }
    public String getCategory() { return category; }
    public Long getItemId() { return itemId; }
    public BigDecimal getQuantitySold() { return quantitySold; }
    public String getScenarioNote() { return scenarioNote; }
}
