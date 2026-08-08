# Design

ConfigService getters from `invite` group (intFromGroup / getStringFromGroup):
- getInviteGenLimit, getInviteCommission, getCommissionFirstTimeEnable, getCommissionAutoCheckEnable
- getCommissionWithdrawLimit, getCommissionWithdrawMethod, getWithdrawCloseEnable
- getCommissionDistributionEnable, getCommissionDistributionL1/L2/L3

Wire InviteController, CommissionSchedule (nested map), TicketController withdraw.
Fix withdraw: `if (commissionBalance < limitCents)`.
OrderService.getConfigInt: also Boolean/String.
