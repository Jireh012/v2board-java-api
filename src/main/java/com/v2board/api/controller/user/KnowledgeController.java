package com.v2board.api.controller.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.v2board.api.common.ApiResponse;
import com.v2board.api.common.BusinessException;
import com.v2board.api.mapper.KnowledgeMapper;
import com.v2board.api.model.Knowledge;
import com.v2board.api.model.User;
import com.v2board.api.service.UserService;
import com.v2board.api.util.Helper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/user/knowledge")
public class KnowledgeController {

    @Autowired
    private KnowledgeMapper knowledgeMapper;

    @Autowired
    private UserService userService;

    @Value("${v2board.app-name:V2Board}")
    private String appName;

    @Value("${v2board.subscribe-path:/api/v1/client/subscribe}")
    private String subscribePath;

    @Value("${v2board.subscribe-url:}")
    private String subscribeUrlConfig;

    @Value("${v2board.show-subscribe-method:0}")
    private Integer subscribeMethod;

    @Value("${v2board.show-subscribe-expire:5}")
    private Integer subscribeExpire;

    /**
     * 对齐 PHP V1\\User\\KnowledgeController::fetch
     */
    @GetMapping("/fetch")
    public ApiResponse<Object> fetch(HttpServletRequest request,
                                     @RequestParam(value = "id", required = false) Long id,
                                     @RequestParam(value = "language", required = false) String language,
                                     @RequestParam(value = "keyword", required = false) String keyword) {
        if (id != null) {
            Knowledge knowledge = knowledgeMapper.selectOne(
                    new LambdaQueryWrapper<Knowledge>()
                            .eq(Knowledge::getId, id)
                            .eq(Knowledge::getShow, 1)
            );
            if (knowledge == null) {
                throw new BusinessException(500, "Article does not exist");
            }
            Object attr = request.getAttribute("user");
            if (!(attr instanceof User user)) {
                throw new BusinessException(401, "Unauthenticated");
            }

            // 不可用用户隐藏 access 区域
            if (!userService.isAvailable(user)) {
                String body = knowledge.getBody();
                body = formatAccessData(body);
                knowledge.setBody(body);
            }

            String subscribeUrl = Helper.getSubscribeUrl(
                    user.getToken(),
                    user.getId(),
                    subscribeMethod,
                    subscribePath,
                    subscribeUrlConfig,
                    subscribeExpire
            );

            String body = knowledge.getBody();
            if (body == null) {
                body = "";
            }
            body = body.replace("{{siteName}}", appName);
            body = body.replace("{{subscribeUrl}}", subscribeUrl);
            body = body.replace("{{urlEncodeSubscribeUrl}}", urlEncode(subscribeUrl));
            body = body.replace("{{safeBase64SubscribeUrl}}", Helper.base64EncodeUrlSafe(subscribeUrl));
            body = body.replace("{{subscribeToken}}", user.getToken());
            knowledge.setBody(body);

            return ApiResponse.success(knowledge);
        }

        LambdaQueryWrapper<Knowledge> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(language)) {
            wrapper.eq(Knowledge::getLanguage, language);
        }
        wrapper.eq(Knowledge::getShow, 1)
                .select(Knowledge::getId, Knowledge::getCategory, Knowledge::getTitle, Knowledge::getUpdatedAt)
                .orderByAsc(Knowledge::getSort);

        if (StringUtils.hasText(keyword)) {
            wrapper.and(q -> q.like(Knowledge::getTitle, keyword)
                    .or()
                    .like(Knowledge::getBody, keyword));
        }

        List<Knowledge> list = knowledgeMapper.selectList(wrapper);
        Map<String, List<Knowledge>> grouped = list.stream()
                .collect(Collectors.groupingBy(k -> k.getCategory() != null ? k.getCategory() : ""));

        return ApiResponse.success(grouped);
    }

    private String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }

    private String formatAccessData(String body) {
        if (body == null || body.isEmpty()) {
            return body;
        }
        String start = "<!--access start-->";
        String end = "<!--access end-->";
        String noAccessHtml = "<div class=\"v2board-no-access\">You must have a valid subscription to view content in this area</div>";
        StringBuilder result = new StringBuilder();
        int index = 0;
        while (true) {
            int startIdx = body.indexOf(start, index);
            if (startIdx == -1) {
                result.append(body.substring(index));
                break;
            }
            int endIdx = body.indexOf(end, startIdx + start.length());
            if (endIdx == -1) {
                result.append(body.substring(index));
                break;
            }
            result.append(body, index, startIdx);
            result.append(noAccessHtml);
            index = endIdx + end.length();
        }
        return result.toString();
    }
}

