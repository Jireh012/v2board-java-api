# Design

## API

- ConfigService getters: `getFrontendThemeSidebar/Header/Color/BackgroundUrl` from `frontend.*`.
- CommController public SM4 plain adds those four string fields (empty bg ok).

## UI

- `PublicSiteConfig` + `siteBrand.loadSiteBrand` → `applyFrontendTheme()`:
  - `document.documentElement.dataset.themeSidebar|themeHeader|themeColor`
  - set `--app-bg-image: url(...)` when bg non-empty; else remove
  - set `--primary-color` (and related) by color map
- `App.vue`: class bindings or rely on `:root[data-theme-header=dark] .app-header` etc. Prefer global CSS in `style.css` for header/sidebar variants so scoped pierce works via `:global` or put rules in style.css unscoped.
- Login page: read same CSS vars / data attrs (already on html).
- AdminSystemConfigView: update 主题 row desc.

Color map suggestion:
- default → #2563eb
- darkblue → #1e3a8a
- black → #0f172a
- green → #059669
