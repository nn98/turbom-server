package com.nextstep.infra.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "unit")
public class UnitEntity {
    @Id
    private String unitId;
    private String sitePnu;
    private String label;
    private String locationSource;

    protected UnitEntity() {
    }

    public String getUnitId() { return unitId; }
    public String getSitePnu() { return sitePnu; }
    public String getLabel() { return label; }
    public String getLocationSource() { return locationSource; }
}
