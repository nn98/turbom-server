# Progress Ledger

Task 1: complete (commits 4b825dc..4699bee, review clean)
Task 2: complete (commits 4699bee..30cf913, review clean after H2 in-memory fix)
Task 3: complete (commits 30cf913..6b50a1e, review clean)
Task 4: complete (commits 6b50a1e..d561c17, review clean)
Task 5: complete (commits d561c17..25c668b, review clean)
Task 6: complete (commits 25c668b..08baf55, review clean; Minor: task-6-report.md misattributes dispatch-prompt wording to the brief, non-blocking)
Task 7: complete (commits 08baf55..4181d81, review clean)
Task 8: complete (commits 4181d81..96f53ed, review clean; Important-deferred: sql.init.mode=always + DROP CASCADE will silently wipe data on restart once a persistent Postgres/Railway datasource is wired in -- out of this plan's scope, flag before deployment)
Task 9: complete (commits 96f53ed..dccef87, review clean, 20/20 suite)
Task 10: complete (commits dccef87..da0a9aa, review clean, 23/23 suite) -- backend-spec.md 6 steps done
Task 11: complete (no code changes, smoke test verified: search/site/unit endpoints 200, error cases 400/404/404, marketInfo isolation confirmed -- sameCategoryNearbyCount null due to sandboxed network, isolation path works)
Final whole-branch review: Approved after 1 fix cycle (SanggaApiClient timeout via RestClientCustomizer, commit 3e7d105). Minor items logged, not fixed: dead findByUnitIdIn, LocalDate.now() for disclaimer/asOf, coordinate-null-check duplication, no timeout-value test. Deferred: sql.init.mode=always + DROP CASCADE risk before Postgres/Railway (out of plan scope).
