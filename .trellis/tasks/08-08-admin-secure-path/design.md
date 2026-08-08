# Design: admin secure_path

## Boundaries

| Layer | Change |
|-------|--------|
| API | `getSecurePath()`；public config field；validate on `save` when `safe` present |
| UI | `siteBrand.adminBasePath` / `adminUrl()`；dynamic routes；AdminLayout + App + pushes |
| Spec | `public-site-config.md` + UI `site-brand.md` |

## Data flow

```
safe.secure_path (DB)
  → getSecurePath() normalize
  → SM4 public config.secure_path
  → loadSiteBrand → adminBasePath
  → router paths `/${base}`, `/${base}/login`, children
  → adminUrl('/orders') => `/${base}/orders`
```

API calls unchanged: `/api/v1/admin/...`.

## Normalization

```
trim
empty → "admin" (for runtime base)
non-empty → must match ^[A-Za-z0-9]{8,}$ and not in RESERVED (except allow "admin" explicitly)
```

Save validation: reject invalid non-empty; allow empty string (means default).

## Router bootstrap

```ts
// main.ts
await loadSiteBrand()
app.use(router) // router module reads adminBasePath.value at init
```

`router.ts` builds admin routes with `adminBasePath` from siteBrand (already loaded).

Guards: `to.path.startsWith('/' + base)` instead of `/admin`.

## Compatibility

- Breaking for bookmarks to `/admin` after custom path set — intended.
- SM4 envelope plaintext gains one field.

## Rollback

Remove public field + revert routes to `/admin`.
