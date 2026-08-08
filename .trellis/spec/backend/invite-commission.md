# Invite & Commission Config

> Runtime readers for nested `invite.*` admin config (gen limit, auto-check, distribution, withdraw).

---

## Scenario: Invite group must be nested

### 1. Scope / Trigger

- Admin saves「邀请 & 佣金」into `v2_system_config` under `invite`.
- Consumers: `InviteController`, `CommissionSchedule`, `TicketController#withdraw`, `OrderService#setInvite`.

### 2. Signatures

```java
int getInviteGenLimit();
int getInviteCommission();
int getCommissionFirstTimeEnable();
int getCommissionAutoCheckEnable();
int getCommissionWithdrawLimit(); // cents
String getCommissionWithdrawMethod();
int getWithdrawCloseEnable();
int getCommissionDistributionEnable();
int getCommissionDistributionL1/L2/L3();
```

### 3. Contracts

| Key | Unit / meaning |
|-----|----------------|
| `invite_gen_limit` | Max unused codes per user |
| `invite_commission` | Default rate % |
| `commission_auto_check_enable` | 0 = schedule skips 0→1 |
| `commission_distribution_*` | Enable + L1/L2/L3 % shares |
| `commission_withdraw_limit` | **Cents** (分); compare with `user.commission_balance` (also cents) |
| `withdraw_close_enable` | 1 = reject withdraw tickets; payout → balance |

- Read via `ConfigService` nested getters / `intFromGroup` — never top-level `full.get("commission_auto_check_enable")`.
- Enable flags accept Number / Boolean / `"0"`/`"1"` (same as other groups).
- `OrderService.getConfigInt` for invite keys also accepts Boolean / numeric String.

### 4. Wrong vs Correct

#### Wrong

```java
long yuan = commissionBalance / 100;
if (withdrawLimit > yuan) { ... } // limit is cents
Object v = getFullConfig().get("commission_auto_check_enable"); // top-level miss
```

#### Correct

```java
if (commissionBalance < configService.getCommissionWithdrawLimit()) { ... }
if (configService.getCommissionAutoCheckEnable() == 0) return;
```

### 5. Tests Required

- ConfigService invite getters: DB override + yml fallback.
- CommissionSchedule: auto-check off → no order updates; payHandle L1/L2/L3 shares when distribution on.
- TicketController withdraw: cents compare; close enable rejects.
- InviteController: gen limit from ConfigService.
- OrderService.getConfigInt: Boolean / String in invite section.
