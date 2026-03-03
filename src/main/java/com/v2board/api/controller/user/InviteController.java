package com.v2board.api.controller.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.v2board.api.common.ApiResponse;
import com.v2board.api.common.BusinessException;
import com.v2board.api.mapper.CommissionLogMapper;
import com.v2board.api.mapper.InviteCodeMapper;
import com.v2board.api.mapper.OrderMapper;
import com.v2board.api.mapper.UserMapper;
import com.v2board.api.model.CommissionLog;
import com.v2board.api.model.InviteCode;
import com.v2board.api.model.User;
import com.v2board.api.util.Helper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/user/invite")
public class InviteController {

    @Autowired
    private InviteCodeMapper inviteCodeMapper;

    @Autowired
    private CommissionLogMapper commissionLogMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private UserMapper userMapper;

    @Value("${v2board.invite-gen-limit:5}")
    private Integer inviteGenLimit;

    @Value("${v2board.invite-commission:10}")
    private Integer defaultInviteCommission;

    @Value("${v2board.commission-distribution-enable:0}")
    private Integer commissionDistributionEnable;

    @Value("${v2board.commission-distribution-l1:100}")
    private Integer commissionDistributionL1;

    /**
     * 生成邀请码，对齐 PHP V1\\User\\InviteController::save
     */
    @PostMapping("/save")
    public ApiResponse<Boolean> save(HttpServletRequest request) {
        User user = requireUser(request);
        Long userId = user.getId();
        long count = inviteCodeMapper.selectCount(
                new LambdaQueryWrapper<InviteCode>()
                        .eq(InviteCode::getUserId, userId)
                        .eq(InviteCode::getStatus, 0)
        );
        if (count >= inviteGenLimit) {
            throw new BusinessException(500, "The maximum number of creations has been reached");
        }
        InviteCode code = new InviteCode();
        code.setUserId(userId);
        code.setCode(Helper.base64EncodeUrlSafe(Helper.getServerKey(System.currentTimeMillis(), 8)));
        code.setStatus(0);
        int rows = inviteCodeMapper.insert(code);
        return ApiResponse.success(rows > 0);
    }

    /**
     * 返利明细，对齐 PHP V1\\User\\InviteController::details
     */
    @GetMapping("/details")
    public ApiResponse<Map<String, Object>> details(HttpServletRequest request,
                                                    @RequestParam(value = "current", required = false, defaultValue = "1") long current,
                                                    @RequestParam(value = "page_size", required = false, defaultValue = "10") long pageSize) {
        User user = requireUser(request);
        if (pageSize < 10) {
            pageSize = 10;
        }
        LambdaQueryWrapper<CommissionLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CommissionLog::getInviteUserId, user.getId())
                .gt(CommissionLog::getGetAmount, 0)
                .orderByDesc(CommissionLog::getCreatedAt);
        Page<CommissionLog> page = new Page<>(current, pageSize);
        Page<CommissionLog> result = commissionLogMapper.selectPage(page, wrapper);
        Map<String, Object> resp = new HashMap<>();
        resp.put("data", result.getRecords());
        resp.put("total", result.getTotal());
        return ApiResponse.success(resp);
    }

    /**
     * 邀请统计 + 邀请码列表，对齐 PHP V1\\User\\InviteController::fetch
     */
    @GetMapping("/fetch")
    public ApiResponse<Map<String, Object>> fetch(HttpServletRequest request) {
        User user = requireUser(request);
        Long userId = user.getId();
        List<InviteCode> codes = inviteCodeMapper.selectList(
                new LambdaQueryWrapper<InviteCode>()
                        .eq(InviteCode::getUserId, userId)
                        .eq(InviteCode::getStatus, 0)
        );
        int commissionRate = defaultInviteCommission;
        if (user.getCommissionRate() != null) {
            commissionRate = user.getCommissionRate();
        }
        long uncheckCommission = orderMapper.selectSumCommissionBalance(userId, 3, 0);
        if (commissionDistributionEnable != null && commissionDistributionEnable == 1) {
            uncheckCommission = uncheckCommission * commissionDistributionL1 / 100;
        }
        long registeredCountLong = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getInviteUserId, userId)
        );
        long validCommission = commissionLogMapper.selectSumGetAmount(userId);
        long availableCommission = user.getCommissionBalance() != null ? user.getCommissionBalance() : 0L;

        long safeValid = Math.max(0, Math.min(validCommission, Integer.MAX_VALUE));
        long safeUncheck = Math.max(0, Math.min(uncheckCommission, Integer.MAX_VALUE));
        long safeAvailable = Math.max(0, Math.min(availableCommission, Integer.MAX_VALUE));

        int[] stat = new int[]{
                (int) Math.max(0, Math.min(registeredCountLong, Integer.MAX_VALUE)),
                (int) safeValid,
                (int) safeUncheck,
                commissionRate,
                (int) safeAvailable
        };
        Map<String, Object> resp = new HashMap<>();
        resp.put("codes", codes);
        resp.put("stat", stat);
        return ApiResponse.success(resp);
    }

    private User requireUser(HttpServletRequest request) {
        Object attr = request.getAttribute("user");
        if (attr instanceof User user) {
            return user;
        }
        throw new BusinessException(401, "Unauthenticated");
    }
}

