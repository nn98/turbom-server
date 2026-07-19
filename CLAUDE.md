# CLAUDE.md — server (turbom-server)

이 백엔드 리포는 넥스트스텝(우아한바톤 해커톤) 프로젝트의 일부. 전체 프로젝트 컨텍스트(팀 조건, 아키텍처, marketInfo 원칙 등)는 상위 폴더의 `../CLAUDE.md`를 참조.

## 스펙 위치

작업 스펙(`api-spec.md`/`frontend-spec.md`/`backend-spec.md`/`schema.sql`/`CHANGELOG.md`/`의사결정-기록.md` 등)은 이 리포에 사본을 두지 않는다. 전부 별도 저장소 [`nn98/turbom-spec`](https://github.com/nn98/turbom-spec)(public)에서 관리한다.

로컬에서는 `turbom-server`(이 리포)·`turbom-client`·`turbom-spec`을 형제 폴더로 clone해서 쓰는 걸 전제로 한다:

```
woowaTon/
├── server/        (turbom-server, 이 리포)
├── ter-view/       (turbom-client)
└── turbom-spec/    (스펙 원본)
```

이 구조에서 스펙은 `../turbom-spec/`으로 참조한다(예: `../turbom-spec/api-spec.md`).

**스펙을 고치면 `../turbom-spec/`에서 직접 수정하고 그 저장소에 커밋·푸시한다** — 이 리포에 사본을 만들거나 되돌려 넣지 않는다. 문서 관리 관례(archive 스냅샷, CHANGELOG 기록)는 `turbom-spec/CHANGELOG.md`에 그대로 있다.

**작업을 마치기 전 `../turbom-spec/`에서 `git push`까지 반드시 끝낸다.** 커밋만 해두고 push를 미루면 `ter-view` 세션이 그 변경을 못 보고 어긋난 전제로 작업한다 — 실제로 이 문제로 프론트 세션의 버그 리포트 2건이 며칠째 미커밋 상태로 묻혀 있다가 2026-07-19 스펙 통합 때 겨우 발견됐다(경위: `turbom-spec/CHANGELOG.md` 17차). 이 리포 자체도 관례상 이 세션 종료 전 `git push origin main`으로 원격과 맞춰둔다.
