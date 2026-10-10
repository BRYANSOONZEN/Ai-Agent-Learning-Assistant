# Team Plan — 软件工程智能学习平台

Stack: **Vue 3 + Element Plus** (frontend), **Spring Boot 3** (backend), MySQL 8.
Split: **by module**. Each person owns their modules end to end: tables, APIs, pages, tests.

Design docs: [需求](requirements.md) · [系统设计与流程图](design.md) · [数据库](database.md) · [接口](api.md) · [测试计划](test-plan.md)

> Open decision: B, C and D write their backend in **Java (same Spring Boot project)** or
> **Python (separate FastAPI service)**. Ownership below is the same either way.

## 1. Ownership

| | **You** | **B** | **C** | **D** |
|---|---|---|---|---|
| Modules | Login/roles + 学 Learn | 记 Record + 诊 Diagnose | 画 Profile | 荐 Recommend + 干 Intervene |
| Team role | Backend lead | Test data + QA lead | Frontend lead | DevOps + docs lead |

Team roles balance the work: C's modules are smaller, so C also owns the shared frontend.

## 2. What each person builds

### You — Login + 学

| Type | Items |
|---|---|
| Tables | `user`, `clazz`, `course`, `enrollment`, `chapter`, `knowledge_point`, `exercise`, `exercise_knowledge_point`, `material` |
| APIs | `POST /auth/login`, `GET /auth/me`; CRUD for courses, chapters, knowledge points, exercises (exercises tagged with knowledge points), materials (file upload) |
| Pages (teacher) | Course management ✅, chapter & knowledge point tree, exercise bank (create/edit questions, tag knowledge points), material upload |
| Pages (student) | Course list ✅, course detail ✅, knowledge point reading page |
| Team role | Spring Boot skeleton, `Result` format, error handling, JWT + roles, DB conventions, review backend PRs |

### B — 记 + 诊

| Type | Items |
|---|---|
| Tables | `answer_record`, `learning_event`, `mastery` |
| APIs | `POST /records/answers` (submit + auto-grade), `GET /records` (my records), `POST /records/events` (view/time logs), `GET /diagnosis/students/{id}`, `GET /diagnosis/classes/{id}` |
| Logic | Auto-grading; mastery per student × knowledge point (start: weighted recent accuracy; later: BKT) |
| Pages | Answering page (quiz), my learning records, diagnosis result (weak points list) |
| Team role | Demo dataset (students, answers, history) for the whole team; test plan |

### C — 画 + frontend lead

| Type | Items |
|---|---|
| Tables | `student_profile` (snapshot), `profile_tag` |
| APIs | `GET /profiles/students/{id}`, `GET /profiles/classes/{id}` (class overview), profile tags (e.g. 勤奋型, 薄弱: 设计模式) |
| Logic | Profile metrics from records + mastery: study time, accuracy trend, activity, tags |
| Pages | Student profile ✅ (connect to real data), teacher class overview dashboard, student list → profile |
| Team role | Layout, theme tokens, shared components, review every PR that changes pages |

### D — 荐 + 干

| Type | Items |
|---|---|
| Tables | `recommendation`, `alert`, `intervention` |
| APIs | `GET /recommendations` (for student), `POST /recommendations/{id}/feedback`; `GET /alerts`; `POST /interventions`, review/approve intervention |
| Logic | Recommend exercises for weak knowledge points; alert rules (low mastery, inactivity, accuracy drop) |
| Pages | Recommendation page (student), alert list, intervention create + teacher review |
| Team role | GitHub setup, CI, Docker Compose, README/docs, final report assembly |

## 3. How modules depend on each other

```
学 (You) ──exercises, knowledge points──► 记 (B) ──answers──► 诊 (B) ──mastery──┬──► 画 (C)
                                                                               └──► 荐 + 干 (D)
```

Nobody waits for upstream work:
- Agree tables and APIs in week 1, then everyone builds against **mock data**.
- B publishes the `mastery` table shape first, since C and D both read it.
- A module uses another module only through its **service**, never its tables or controllers directly.

## 4. Milestones

| Phase | Goal | Done when |
|---|---|---|
| **W1 Design** | Wireframes, tables, API list, M1–M10 mapping | Whole team approved `docs/database.md` and the API doc |
| **W2–3 Build** | Each module's APIs + pages on mock data | Every page works in mock mode; APIs pass their tests |
| **W4 Integrate** | Full chain on real data: learn → answer → diagnose → profile → recommend → alert | Demo: one student goes through the whole chain |
| **W5 Polish** | Bugs, tests, demo data, docs | Fresh clone runs with one command; README complete |
| **After** | Optional features E1–E7 | Assigned once the required chain is done |

Weekly: 15-minute demo, everyone shows their pages side by side.

## 5. Requirements mapping

Moved to [requirements.md §7](requirements.md#7-需求编号映射m1m10) — fill in the M numbers there.

Optional modules chosen: **E1 5E learning agent** and **E4 key & difficult points** — see [requirements.md §5.6](requirements.md#56-选做模块). Both start after W4.

## 6. Rules

- Branch per task: `feat/<module>-<task>`, e.g. `feat/records-submit-answer`. PRs into `dev`; `dev` → `main` at each milestone.
- Commits: `feat(records): submit answer API`, `fix(profile): chart overflow`.
- Every PR: CI passes + 1 approval (C for pages, You for backend core).
- Frontend design rules: see `frontend/README.md`.
- One GitHub issue per task, tracked on the Projects board.
