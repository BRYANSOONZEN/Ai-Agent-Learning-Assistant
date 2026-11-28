# Studywise · AI learning assistant

Vue 3 + Vite frontend prototype for the 第 2 组 AI learning assistant.

## Run locally

```bash
npm install
npm run dev
```

## Feature folders

```text
src/
├── components/       # shared shell and icons
├── data/              # demo content and learning evidence
└── features/
    ├── dashboard/     # learner overview
    ├── learning/      # E1 5E learning flow, hints, answer submission
    ├── materials/     # principle/example material split and visibility rules
    ├── progress/      # E4 mastery evidence and learning events
    └── teacher/       # M10 teacher review and intervention draft
```

## Scope represented in this prototype

- E1 5E flow with Explore → Explain → Evaluate movement.
- L1–L3 hint ceiling; prompts guide before explaining and do not expose the answer.
- Student-facing sources are principle sentences only; example-answer fragments stay teacher-only.
- Answer submission produces a visible learning event and evidence trail.
- E4 personal mastery cards and evidence timeline.
- M10 teacher review with human confirmation before a judgement is applied.

All data is local demo state for the frontend prototype. API/database integration can be added behind the same feature boundaries later.
