package com.netzero.store.domain;

import com.netzero.common.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "order_policy")
public class OrderPolicy extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private ItemMaster item;

    @Column(name = "item_name")
    private String itemName;

    private String category;

    @Column(name = "order_method")
    private String orderMethod;

    @Column(name = "order_cycle_days", nullable = false)
    private int orderCycleDays;

    @Column(name = "lead_time_days", nullable = false)
    private int leadTimeDays;

    @Column(name = "safety_z")
    private BigDecimal safetyZ;

    @Column(name = "order_lot_unit")
    private BigDecimal orderLotUnit;

    private String note;

    protected OrderPolicy() {}

    public Store getStore() { return store; }
    public Long getStoreId() { return store != null ? store.getId() : null; }
    public ItemMaster getItem() { return item; }
    public Long getItemId() { return item != null ? item.getId() : null; }
    public String getItemName() { return itemName; }
    public String getCategory() { return category; }
    public String getOrderMethod() { return orderMethod; }
    public int getOrderCycleDays() { return orderCycleDays; }
    public int getLeadTimeDays() { return leadTimeDays; }
    public BigDecimal getSafetyZ() { return safetyZ; }
    public BigDecimal getOrderLotUnit() { return orderLotUnit; }
    public String getNote() { return note; }
}
