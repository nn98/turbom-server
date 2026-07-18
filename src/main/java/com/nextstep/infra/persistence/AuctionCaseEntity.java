package com.nextstep.infra.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "auction_case")
public class AuctionCaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String caseNumber;
    @Column(nullable = false)
    private Integer itemNumber;
    private String court;
    private String divisionName;
    private String propertyType;
    @Column(length = 300)
    private String jibunAddress;
    private BigDecimal appraisalValueKrw;
    private BigDecimal minimumSalePriceKrw;
    private BigDecimal bidDepositKrw;
    private String biddingMethod;
    private String saleDate;
    private String filedDate;
    private String auctionStartDate;
    private String claimDeadline;
    private BigDecimal claimAmountKrw;
    @Column(length = 4000)
    private String appraisalSummary;

    @OneToMany(mappedBy = "auctionCase", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AuctionScheduleEntryEntity> scheduleHistory = new ArrayList<>();

    protected AuctionCaseEntity() {
    }

    public AuctionCaseEntity(String caseNumber, Integer itemNumber, String court, String divisionName,
                              String propertyType, String jibunAddress, BigDecimal appraisalValueKrw,
                              BigDecimal minimumSalePriceKrw, BigDecimal bidDepositKrw, String biddingMethod,
                              String saleDate, String filedDate, String auctionStartDate, String claimDeadline,
                              BigDecimal claimAmountKrw, String appraisalSummary) {
        this.caseNumber = caseNumber;
        this.itemNumber = itemNumber;
        this.court = court;
        this.divisionName = divisionName;
        this.propertyType = propertyType;
        this.jibunAddress = jibunAddress;
        this.appraisalValueKrw = appraisalValueKrw;
        this.minimumSalePriceKrw = minimumSalePriceKrw;
        this.bidDepositKrw = bidDepositKrw;
        this.biddingMethod = biddingMethod;
        this.saleDate = saleDate;
        this.filedDate = filedDate;
        this.auctionStartDate = auctionStartDate;
        this.claimDeadline = claimDeadline;
        this.claimAmountKrw = claimAmountKrw;
        this.appraisalSummary = appraisalSummary;
    }

    public void addScheduleEntry(AuctionScheduleEntryEntity entry) {
        scheduleHistory.add(entry);
    }

    public Long getId() { return id; }
    public String getCaseNumber() { return caseNumber; }
    public Integer getItemNumber() { return itemNumber; }
    public String getCourt() { return court; }
    public String getDivisionName() { return divisionName; }
    public String getPropertyType() { return propertyType; }
    public String getJibunAddress() { return jibunAddress; }
    public BigDecimal getAppraisalValueKrw() { return appraisalValueKrw; }
    public BigDecimal getMinimumSalePriceKrw() { return minimumSalePriceKrw; }
    public BigDecimal getBidDepositKrw() { return bidDepositKrw; }
    public String getBiddingMethod() { return biddingMethod; }
    public String getSaleDate() { return saleDate; }
    public String getFiledDate() { return filedDate; }
    public String getAuctionStartDate() { return auctionStartDate; }
    public String getClaimDeadline() { return claimDeadline; }
    public BigDecimal getClaimAmountKrw() { return claimAmountKrw; }
    public String getAppraisalSummary() { return appraisalSummary; }
    public List<AuctionScheduleEntryEntity> getScheduleHistory() { return scheduleHistory; }
}
