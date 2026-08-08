# Design

- UniProxyController config: extend base_config map with min traffic ints from server section.
- ConfigService.save: validateServerGroup — token length ≥16 when non-empty key present; intervals ≥1; min traffic ≥0.
- resolveNodeContext / V2Server: reject blank or short configured token.
- Admin UI: generate 32-char random token button next to input.
- Spec note in system-config or new server-node.md brief.
