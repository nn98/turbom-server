# Turbom Frontend Integration Contract

English translation of `프론트-연동계약서.md`, updated to match the current
implementation (2026-07-10/11 session: `totalStoreCount`/`categoryBreakdown`
added to `marketInfo`, `status` corrected to not be a fixed 3-value enum,
`parsedFloor`/`parsedUnitNo`/`parseConfidence` documented). Full detail
lives in `api-spec.md`/`backend-spec.md`; this is the one-page essentials
for frontend integration.

## Domain (what the frontend needs to know)

```
Site (a plot, PNU) ──1:N── Unit (a storefront) ──1:N── Tenancy (a lease/history entry)
```

- **Site**: one jibun (lot) address = one building/parcel. Key = `pnu` (19-digit string).
- **Unit**: an individual storefront within a Site. In the real dataset, Site:Unit is almost always 1:1 (room/floor separation is rare). Key = `unitId`.
- **Tenancy**: one business that occupied a Unit for some period, in chronological order. Each Tenancy is one "cell" of the timeline.
- **Lookup key = PNU**. Address search → PNU → units → tenancy history.

## The 3 endpoints

| # | Method & path | Screen | Live sangga API call |
|---|---|---|---|
| 1 | `GET /api/sites/search?query={address}` | Landing search | None |
| 2 | `GET /api/sites/{pnu}` | Site detail (unit list) | None |
| 3 | `GET /api/units/{unitId}` | Unit detail (timeline + per-tenancy marketInfo) | **Only here** |

- Endpoints 1 and 2 are pure DB reads → fast, always succeed.
- Endpoint 3 is the only one that calls the external market API live. The `marketInfo` fields sourced from that call degrade independently to `null`/`[]` on failure — the request as a whole still returns 200.

## Response types (TypeScript)

```ts
// ── Common ──
interface Disclaimer { dataAsOf: string; note: string; }  // present on every response
interface ApiError { error: string; message: string; }

// ── ① search ──
interface Candidate {
  pnu: string;
  jibunAddress: string;
  roadAddress: string;
  latitude: number | null;
  longitude: number | null;
  unitCount: number;      // number of units at this site
  closedCount: number;    // cumulative closures at this site
}
interface SearchResponse { candidates: Candidate[]; }

// ── ② site detail ──
type LocationSource = "license" | "sangga_api" | "overlap_inferred";
type ParseConfidence = "HIGH" | "LOW" | null;
interface UnitSummary {
  unitId: string;
  label: string;                       // "단일 점포" (single unit) | room/floor number | "물건 A" (Unit A)
  currentBusinessName: string | null;  // current occupant name, null if vacant
  currentStatus: "영업" | "공실";       // "operating" | "vacant"
  totalTenancyCount: number;
  closedCount: number;
  averageSurvivalMonths: number | null;
  industryDetail: string | null;       // sangga API detailed category (only for the current tenant)
  locationSource: LocationSource;
  parsedFloor: string | null;          // offline-parsed from roadAddress, see backend-spec.md §3.2
  parsedUnitNo: string | null;
  parseConfidence: ParseConfidence;    // trust `label`'s parsed detail only when "HIGH"
}
interface SiteDetail {
  site: { pnu: string; jibunAddress: string; roadAddress: string;
          latitude: number | null; longitude: number | null; };
  units: UnitSummary[];
  disclaimer: Disclaimer;
}

// ── ③ unit detail ──
type EnrichmentSource = "sangga_api" | "license_only";
interface Statistics {
  totalTenancyCount: number;
  closedCount: number;
  averageSurvivalMonths: number | null;
  longestSurvivalMonths: number | null;
  shortestSurvivalMonths: number | null;
}
interface CategoryCount {
  code: string;    // sangga top-level category code, e.g. "I2"
  name: string;    // sangga top-level category name, e.g. "음식" (Food)
  count: number;    // stores of this category within the radius
  ratio: number;    // count / totalStoreCount, 0~1
}
interface MarketInfo {
  isPlaceholder: boolean;                 // currently always true
  leaseAreaSqm: number | null;            // mock — no data source exists (do not attempt to source this)
  depositKrw: number | null;              // mock
  monthlyRentKrw: number | null;          // mock
  keyMoneyKrw: number | null;             // mock
  dailyFloatingPopulation: number | null; // mock
  vacancyRatePercent: number | null;      // mock
  sameCategoryNearbyCount: number | null; // REAL (sangga API) — same-category stores near this unit's current category, within 300m
  totalStoreCount: number | null;         // REAL (sangga API) — all stores within the same 300m radius, no category filter
  categoryBreakdown: CategoryCount[];     // REAL (sangga API) — per-category count/ratio; empty if unavailable. Frontend can use this to let a user pick any category and show that category's count + competition ratio, not just the unit's own category.
  asOf: string;
}
interface Tenancy {
  tenancyId: string;
  businessName: string;
  category: string;              // license major category (e.g. "음식"/Food) — always present
  subCategory: string;           // license minor category (e.g. "일반음식점"/general restaurant) — always present
  industryDetail: string | null; // sangga API detailed category — only for the currently-operating tenancy
  licensedAt: string;            // YYYY-MM-DD
  closedAt: string | null;       // null = still operating
  status: string;                // "영업" (operating) is normalized from the raw "영업/정상"; every other raw status string (e.g. "취소/말소/만료/정지/중지") passes through as-is — do NOT treat this as a fixed 3-value enum
  survivalMonths: number | null;
  closedAtEstimated: boolean;    // always false for the current dataset (real closure dates)
  enrichmentSource: EnrichmentSource;
  marketInfo: MarketInfo;
}
interface UnitDetail {
  unit: { unitId: string; label: string; jibunAddress: string; roadAddress: string;
          parsedFloor: string | null; parsedUnitNo: string | null; parseConfidence: ParseConfidence; };
  statistics: Statistics;
  timeline: Tenancy[];           // ascending by licensedAt (oldest → newest)
  disclaimer: Disclaimer;
}
```

## What each screen uses

**① Landing** — `search(query)` → candidate list (jibun/road address, unit count, closed count). Click → `/map?q=&pnu=` (as of 2026-07-10, this is a map + query-string route, not a path param — see `frontend-spec.md` §3).

**② Site detail** — `getSite(pnu)` → map (show "no location" if coordinates are null) + unit card list. Click a card → `/units/:unitId`.

**③ Unit detail** — `getUnit(unitId)` →
- 4 stat cards (tenancies passed through / closures / average survival / longest & shortest)
- Operating timeline (horizontal bar, segment width proportional to survival months)
- "View store details" dropdown (select by `tenancyId`) → left: license info (businessName/category/subCategory/period/status) · right: `marketInfo` — `sameCategoryNearbyCount`/`totalStoreCount`/`categoryBreakdown` are real, the other 5 fields are mock (always show the "example" badge + "예시값입니다" caption for those, regardless of `isPlaceholder`)

## The 3-field category distinction (easy to mix up)

| Field | Source | Presence | Where shown |
|---|---|---|---|
| `category` | License major category | Always | Timeline default display |
| `subCategory` | License minor category | Always | Shown on unit-detail click |
| `industryDetail` | sangga API detail | Only while operating | Preferred over `subCategory` when present |

## Errors

| code | HTTP | When |
|---|---|---|
| INVALID_QUERY | 400 | `query` missing/blank |
| SITE_NOT_FOUND | 404 | `pnu` not found |
| UNIT_NOT_FOUND | 404 | `unitId` not found |
| INTERNAL_ERROR | 500 | Server error |

- An empty search result is not an error → `{ candidates: [] }`, 200.
- A failed `sameCategoryNearbyCount`/`totalStoreCount`/`categoryBreakdown` lookup is not an error → only that field is `null`/`[]`, still 200. These three are independent external calls and can fail independently of each other.

## Mock ↔ real switch

- `VITE_API_BASE_URL` unset = mock mode (frontend works standalone). Set = real API.
- Mock data is fully specified in `frontend-spec.md` §6 (2 sites, 4 units, several tenancy entries, and an intentional empty-result case for "시흥동 123").
