package com.nextstep.infra.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "site")
public class SiteEntity {
    @Id
    private String pnu;
    private String jibunAddress;
    private String roadAddress;
    private BigDecimal longitude;
    private BigDecimal latitude;
    @Column(name = "original_x")
    private BigDecimal originalX;
    @Column(name = "original_y")
    private BigDecimal originalY;
    private Boolean addressCorrected;
    private String localGovCode;

    protected SiteEntity() {
    }

    public String getPnu() { return pnu; }
    public String getJibunAddress() { return jibunAddress; }
    public String getRoadAddress() { return roadAddress; }
    public BigDecimal getLongitude() { return longitude; }
    public BigDecimal getLatitude() { return latitude; }
}
