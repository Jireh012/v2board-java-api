# Design — Panel API action aliases

## Algorithm

```
classicRel = path without leading/trailing "/"
input = SM4_KEY + "\0" + zone + "\0" + classicRel   // zone ∈ {passport,user,admin}
alias = hex(SHA-256(UTF-8(input)))[0:12]            // [0-9a-f], length 12
external = {zonePrefix} + "/" + alias
internal = "/api/v1/" + zone + "/" + classicRel
```

`/config` bootstrap is unchanged (fixed path, not an action alias).

## Catalog

`PanelApiActionCatalog` lists every classicRel used by passport/user/admin controllers (including per-protocol `server/{vmess|…}/…`). Startup builds reverse map; duplicate aliases → fail fast.

## Filter

`ClientApiPathFilter` after prefix match:

1. Remainder empty or multi-segment → 404  
2. Single segment not in reverse map → 404  
3. Else rewrite to internal classic path + mark Panel SM4 zone  

## Frontend

`apiUrl(zone, '/order/fetch')` normalizes then derives alias with `VITE_SM4_KEY`. Call sites keep classic relative strings in source (developer ergonomics); wire format is opaque.

## Key spaces

Panel action aliases use **panel** `SM4_KEY`, same as body/`X-A` SM4 — not `server_token`.
