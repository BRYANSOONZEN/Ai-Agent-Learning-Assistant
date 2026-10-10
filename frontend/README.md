# 前端 Frontend — 软件工程智能学习平台

Vue 3 + Vite + Element Plus + ECharts + Pinia + Vue Router (JavaScript)

## Run it

```bash
npm install
npm run dev        # http://localhost:5173
```

By default it runs with **mock data** (`VITE_USE_MOCK=true` in `.env.development`), so no backend is needed.
Log in with any username and password and pick a role.

To use the real Spring Boot backend (port 8080), set `VITE_USE_MOCK=false`. Requests to `/api/*` are proxied to it.

Other commands: `npm run lint`, `npm run format`, `npm run build`.

## Folder structure

```
src/
├── api/            One file per backend module. Each function = one endpoint (+ mock branch)
├── mock/           Fake data, same shape as the API contract
├── components/     Shared components — use these, don't rebuild them
├── layout/         Sidebar + header shell (frontend lead owns this)
├── router/         All routes. The sidebar menu is generated from here
├── stores/         Pinia stores (logged-in user)
├── styles/
│   ├── tokens.css      ⭐ All colors, spacing, radius, fonts
│   ├── global.css      .app-card, .app-toolbar, .app-pagination
│   └── chart-theme.js  Shared chart colors
└── views/          Pages, one folder per module
```

## Page templates — copy these, don't start from scratch

| Need | Copy | Example |
|---|---|---|
| List with search + table + pagination | `views/learn/CourseList.vue` | 课程学习 |
| Create/edit form | `views/learn/CourseFormDialog.vue` | 新建课程 dialog |
| Detail of one item | `views/learn/CourseDetail.vue` | click a course |
| Dashboard with stats + charts | `views/profile/ProfileView.vue` | 学生画像 |

## Adding a new page

1. Copy the closest template into `src/views/<module>/`.
2. Add API functions in `src/api/<module>.js` (follow `api/course.js`) and mock data in `src/mock/data.js`.
3. Register the route in `src/router/index.js`. The menu updates automatically.

## Design rules (required — reviewers will check)

1. **Use Element Plus components.** Never hand-style a button, table, input, dialog or tag.
2. **No raw colors, spacing or font sizes.** Use the variables in `styles/tokens.css`,
   e.g. `padding: var(--app-space-lg)`, not `padding: 22px`. Need a new value? Add it to tokens.css in its own PR.
3. **Every page starts with `<PageHeader>`.** Page-level buttons go in its slot.
4. **Content sits in `.app-card` blocks.** Charts use `<ChartCard>`, numbers use `<StatCard>`.
5. **Text is Chinese**, consistent wording: 新建 / 编辑 / 删除 / 保存 / 取消 / 查看.
6. **Reused UI goes in `components/`.** If two pages need it, it's a shared component.
7. **API calls only go through `src/api/`**, never `axios` directly in a page.
8. PRs that change pages include a screenshot.

## Backend response format

The backend always returns:

```json
{ "code": 200, "message": "ok", "data": { } }
```

`api/request.js` returns `data` to the page, and shows an error message automatically when `code !== 200`.
List endpoints return `data: { records: [...], total: 123 }` (MyBatis-Plus page format).
