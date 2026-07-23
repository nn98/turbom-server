package com.nextstep.application;

import com.nextstep.infra.persistence.LicensedBusinessRecordEntity;
import java.lang.reflect.Field;
import java.time.LocalDate;

final class TestFixtures {

    private TestFixtures() {
    }

    static LicensedBusinessRecordEntity record(long id, String category, String subCategory,
                                                String businessName, String parsedBuildingName,
                                                String parsedFloor, String parsedUnitNo,
                                                String parseConfidence) {
        LicensedBusinessRecordEntity entity = newInstance();
        set(entity, "id", id);
        set(entity, "pnu", "4113110800105090000");
        set(entity, "category", category);
        set(entity, "subCategory", subCategory);
        set(entity, "businessName", businessName);
        set(entity, "businessStatus", "영업/정상");
        set(entity, "licensedAt", LocalDate.of(2020, 1, 1));
        set(entity, "jibunAddress", "경기도 성남시 수정구 테스트동 1");
        set(entity, "addressSeparated", false);
        set(entity, "parsedBuildingName", parsedBuildingName);
        set(entity, "parsedFloor", parsedFloor);
        set(entity, "parsedUnitNo", parsedUnitNo);
        set(entity, "parseConfidence", parseConfidence);
        return entity;
    }

    static LicensedBusinessRecordEntity recordForOverlap(long id, String category, String subCategory,
                                                           String businessName, String parsedBuildingName,
                                                           String parsedFloor, String parsedUnitNo,
                                                           String parseConfidence, LocalDate licensedAt,
                                                           LocalDate closedAt, String jibunAddress,
                                                           String roadAddress) {
        LicensedBusinessRecordEntity entity = record(id, category, subCategory, businessName,
            parsedBuildingName, parsedFloor, parsedUnitNo, parseConfidence);
        set(entity, "licensedAt", licensedAt);
        set(entity, "closedAt", closedAt);
        set(entity, "jibunAddress", jibunAddress);
        set(entity, "roadAddress", roadAddress);
        return entity;
    }

    private static LicensedBusinessRecordEntity newInstance() {
        try {
            var ctor = LicensedBusinessRecordEntity.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void set(LicensedBusinessRecordEntity entity, String field, Object value) {
        try {
            Field f = LicensedBusinessRecordEntity.class.getDeclaredField(field);
            f.setAccessible(true);
            f.set(entity, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
