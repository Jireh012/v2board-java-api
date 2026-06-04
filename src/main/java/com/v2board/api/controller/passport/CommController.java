package com.v2board.api.controller.passport;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.v2board.api.common.ApiResponse;
import com.v2board.api.mapper.InviteCodeMapper;
import com.v2board.api.model.InviteCode;
import com.v2board.api.service.PassportService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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
