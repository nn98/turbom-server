# Task 3: Domain Value Objects — Completion Report

## Status: DONE

All value objects implemented following TDD methodology. 3/3 tests passing.

## Commit Hash
`6b50a1e`

## Test Results
```
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
TenancyPeriodTest:
  ✓ 폐업이면_인허가일부터_폐업일까지_개월수를_계산한다()
  ✓ 영업중이면_폐업일이_null이다()
  ✓ 폐업일이_인허가일보다_빠르면_예외()
```

## Implementation Summary

Implemented 5 immutable domain value objects following the brief specification:

1. **Pnu** (site domain)
   - Record-based with canonical constructor validation
   - Validates 19-digit PNU format
   - Provides `legalDongCode()` extraction method

2. **Coordinate** (site domain)
   - Simple record: `(double latitude, double longitude)`
   - No validation (coordinates are presumed valid from source)

3. **LocationSource** (unit domain)
   - Enum with 3 values: LICENSE, SANGGA_API, OVERLAP_INFERRED
   - Provides `dbValue()` for persistence mapping
   - Provides `fromDb(String)` for deserialization

4. **BusinessStatus** (tenancy domain)
   - Enum with 3 values: ACTIVE("영업"), CLOSED("폐업"), SUSPENDED("휴업")
   - Provides `display()` for UI rendering
   - Provides `fromDb(String)` for deserialization

5. **TenancyPeriod** (tenancy domain)
   - Record-based with canonical constructor validation
   - Enforces: licensedAt is required, closedAt ≥ licensedAt
   - Provides `survivalMonths()` using ChronoUnit.MONTHS.between()
   - Handles active tenancies (closedAt = null) by using LocalDate.now()

## Design Decisions

- **Record types** for immutability and minimal boilerplate
- **Enum bidirectional mapping** (dbValue ↔ fromDb) for persistence layer integration
- **Validation in canonical constructors** to enforce invariants at construction time
- **No Spring/JPA dependencies** — pure domain layer as specified
- **DateTime semantics** — months calculation uses calendar month boundaries per ISO-8601

## Files Created

```
src/main/java/com/nextstep/domain/
  ├── site/
  │   ├── Pnu.java
  │   └── Coordinate.java
  ├── unit/
  │   └── LocationSource.java
  └── tenancy/
      ├── BusinessStatus.java
      └── TenancyPeriod.java

src/test/java/com/nextstep/domain/
  └── tenancy/
      └── TenancyPeriodTest.java
```

## Next Steps

- Implement entity layer (Site, Unit, Tenancy) using these value objects
- Wire persistence mapping for enums (LocationSource, BusinessStatus)
- Implement repository/query layer for API endpoints
