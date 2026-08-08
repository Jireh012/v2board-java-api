package com.v2board.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.v2board.api.common.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/**
 * Google reCAPTCHA v2 siteverify (PHP ReCaptcha parity for {@code recaptcha_data}).
 */
@Service
public class RecaptchaService {

    private static final String SITEVERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";

    @Autowired
    private ConfigService configService;

    @Autowired
    private ObjectMapper objectMapper;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * When {@code safe.recaptcha_enable=1}, require a valid token; otherwise no-op.
     */
    public void verifyIfEnabled(String recaptchaData, String remoteIp) {
        if (configService.getRecaptchaEnable() != 1) {
            return;
        }
        String secret = configService.getRecaptchaSecret();
        if (!StringUtils.hasText(secret)) {
            throw new BusinessException(500, "reCAPTCHA 未正确配置");
        }
        if (!StringUtils.hasText(recaptchaData)) {
            throw new BusinessException(500, "Invalid code is incorrect");
        }
        if (!verifyWithGoogle(secret.trim(), recaptchaData.trim(), remoteIp)) {
            throw new BusinessException(500, "Invalid code is incorrect");
        }
    }

    boolean verifyWithGoogle(String secret, String response, String remoteIp) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", secret);
        form.add("response", response);
        if (StringUtils.hasText(remoteIp)) {
            form.add("remoteip", remoteIp);
        }
        try {
            String body = restTemplate.postForObject(SITEVERIFY_URL, form, String.class);
            if (!StringUtils.hasText(body)) {
                return false;
            }
            JsonNode node = objectMapper.readTree(body);
            return node.path("success").asBoolean(false);
        } catch (Exception e) {
            throw new BusinessException(500, "Invalid code is incorrect");
        }
    }
}
