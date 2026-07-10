# Task 7 Report

## Status
**DONE**

## Commit Hash
`4181d81`

## Summary
MarketInfo value object (record) and 3 domain exception classes (InvalidQueryException, SiteNotFoundException, UnitNotFoundException) added to `com.nextstep.domain.*` packages; mvn compile successful.

## Details
- **Step 1**: MarketInfo.java with record definition, 6 static final placeholders, and `unavailable()`/`of()` factory methods ✓
- **Step 2**: Three RuntimeException subclasses created with appropriate error messages in Korean ✓
- **Step 3**: `mvn -q compile` ran successfully (no errors) ✓
- **Step 4**: All 4 files staged and committed with message "feat: add MarketInfo value object and domain exceptions" ✓

## Files Created
```
src/main/java/com/nextstep/domain/market/MarketInfo.java
src/main/java/com/nextstep/domain/exception/InvalidQueryException.java
src/main/java/com/nextstep/domain/exception/SiteNotFoundException.java
src/main/java/com/nextstep/domain/exception/UnitNotFoundException.java
```
