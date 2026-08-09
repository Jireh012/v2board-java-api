# Implement: External subscribe pre-proxy

## Checklist

1. [x] DDL: `pre_proxy_enable` in `v2_external_subscribe.sql`
2. [x] Model + admin save/fetch
3. [x] `ExternalSubscribeFetcher.fetch(url, Proxy)`
4. [x] `SingBoxProbeService.openHttpProxy` session
5. [x] Sync auto-pick + fetch via proxy
6. [x] UI Toggle + list badge
7. [x] Spec scenario
8. [x] `ExternalSubscribePreProxyTest`

## Validation

```bash
mvn -q -Dtest=ExternalSubscribePreProxyTest test
# Apply ALTER on DB (see SQL comments), restart API, toggle + sync
```

## Rollback

Set `pre_proxy_enable=0`; revert commits.
