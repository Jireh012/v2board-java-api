package com.v2board.api.controller.passport;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.v2board.api.common.ApiResponse;
import com.v2board.api.mapper.InviteCodeMapper;
import com.v2board.api.model.InviteCode;
import com.v2board.api.service.ConfigService;
import com.v2board.api.service.PassportService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 对齐 PHP V1\Passport\CommController
 */
@RestController
@RequestMapping("/api/v1/passport/comm")
public class CommController {

    @Autowired
    private PassportService passportService;

    @Autowired
    private InviteCodeMapper inviteCodeMapper;

    @Autowired
    private ConfigService configService;

    /**
     * 公开站点配置（仅非敏感字段），供登录/注册页与全站品牌渲染。
     */
    @GetMapping("/config")
    public ApiResponse<Map<String, Object>> config() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("app_name", configService.getAppName());
        data.put("stop_register", configService.getStopRegister());
        data.put("invite_force", configService.getInviteForce());
        return ApiResponse.success(data);
    }

    @PostMapping("/sendEmailVerify")
    public ApiResponse<Boolean> sendEmailVerify(@RequestBody Map<String, Object> body,
                                                HttpServletRequest request) throws Exception {
        return ApiResponse.success(passportService.sendEmailVerify(body, request.getRemoteAddr()));
    }

    @PostMapping("/pv")
    public ApiResponse<Boolean> pv(@RequestBody Map<String, Object> body) {
        Object codeObj = body.get("invite_code");
        if (codeObj != null) {
            InviteCode invite = inviteCodeMapper.selectOne(
                    new LambdaQueryWrapper<InviteCode>().eq(InviteCode::getCode, String.valueOf(codeObj)));
            if (invite != null) {
                invite.setPv((invite.getPv() != null ? invite.getPv() : 0) + 1);
                inviteCodeMapper.updateById(invite);
            }
        }
        return ApiResponse.success(true);
    }
}
