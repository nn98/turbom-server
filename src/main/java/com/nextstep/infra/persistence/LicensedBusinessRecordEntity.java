package com.nextstep.infra.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "licensed_business_record")
public class LicensedBusinessRecordEntity {
    @Id
    private Long id;
    private String pnu;
    private String category;
    private String subCategory;
    private String licenseNo;
    private String businessName;
    private String businessType;
    private String businessStatus;
    private String statusDetailCode;
    private String statusDetail;
    private LocalDate licensedAt;
    private LocalDate closedAt;
    private String roadAddress;
    private String jibunAddress;
    private Boolean addressSeparated;
    private Boolean addressCorrected;
    private String parsedBuildingName;
    private String parsedFloor;
    private String parsedUnitNo;
    private String parseConfidence;
    private String parseMethod;
    private String localGovCode;
    @Column(name = "original_x")
    private BigDecimal originalX;
    @Column(name = "original_y")
    private BigDecimal originalY;

    protected LicensedBusinessRecordEntity() {
    }

    public Long getId() { return id; }
    public String getPnu() { return pnu; }
    public String getCategory() { return category; }
    public String getSubCategory() { return subCategory; }
    public String getBusinessName() { return businessName; }
    public String getBusinessStatus() { return businessStatus; }
    public LocalDate getLicensedAt() { return licensedAt; }
    public LocalDate getClosedAt() { return closedAt; }
    public String getRoadAddress() { return roadAddress; }
    public String getJibunAddress() { return jibunAddress; }
    public String getParsedBuildingName() { return parsedBuildingName; }
    public String getParsedFloor() { return parsedFloor; }
    public String getParsedUnitNo() { return parsedUnitNo; }
    public String getParseConfidence() { return parseConfidence; }
    public String getParseMethod() { return parseMethod; }
    public BigDecimal getOriginalX() { return originalX; }
    public BigDecimal getOriginalY() { return originalY; }
}
