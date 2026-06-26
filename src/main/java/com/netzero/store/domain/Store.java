package com.netzero.store.domain;

import com.netzero.common.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "store")
public class Store extends BaseEntity {

    private String name;
    private String region;
    private Integer nx;
    private Integer ny;

    protected Store() {}

    public String getName() { return name; }
    public String getRegion() { return region; }
    public Integer getNx() { return nx; }
    public Integer getNy() { return ny; }
}
