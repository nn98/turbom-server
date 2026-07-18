package com.nextstep.infra.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "auction_schedule_entry")
public class AuctionScheduleEntryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private AuctionCaseEntity auctionCase;

    private String scheduleDate;
    private String scheduleTime;
    private String scheduleType;
    @Column(length = 300)
    private String location;
    private BigDecimal minimumPriceKrw;
    private String result;

    protected AuctionScheduleEntryEntity() {
    }

    public AuctionScheduleEntryEntity(AuctionCaseEntity auctionCase, String scheduleDate, String scheduleTime,
                                       String scheduleType, String location, BigDecimal minimumPriceKrw,
                                       String result) {
        this.auctionCase = auctionCase;
        this.scheduleDate = scheduleDate;
        this.scheduleTime = scheduleTime;
        this.scheduleType = scheduleType;
        this.location = location;
        this.minimumPriceKrw = minimumPriceKrw;
        this.result = result;
    }

    public Long getId() { return id; }
    public String getScheduleDate() { return scheduleDate; }
    public String getScheduleTime() { return scheduleTime; }
    public String getScheduleType() { return scheduleType; }
    public String getLocation() { return location; }
    public BigDecimal getMinimumPriceKrw() { return minimumPriceKrw; }
    public String getResult() { return result; }
}
