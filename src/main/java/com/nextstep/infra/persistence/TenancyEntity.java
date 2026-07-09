package com.nextstep.infra.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "tenancy_record")
public class TenancyEntity {
    @Id
    private Long id;
    private String unitId;
    private String licenseNo;
    private String businessName;
    private String category;
    private String subCategory;
    private LocalDate licensedAt;
    private LocalDate closedAt;
    private String status;
    private String statusDetail;

    protected TenancyEntity() {
    }

    public Long getId() { return id; }
    public String getUnitId() { return unitId; }
    public String getBusinessName() { return businessName; }
    public String getCategory() { return category; }
    public String getSubCategory() { return subCategory; }
    public LocalDate getLicensedAt() { return licensedAt; }
    public LocalDate getClosedAt() { return closedAt; }
    public String getStatus() { return status; }
}
