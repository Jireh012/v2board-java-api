package com.v2board.api.controller.guest;

import com.v2board.api.service.TelegramBotHandler;
import com.v2board.api.service.TelegramService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GuestTelegramControllerTest {

    @Test
    void webhook_rejectsWrongAccessTokenWith401() {
        TelegramService telegramService = mock(TelegramService.class);
        TelegramBotHandler handler = mock(TelegramBotHandler.class);
        when(telegramService.getBotToken()).thenReturn("secret-bot-token");

        GuestTelegramController controller = new GuestTelegramController();
        ReflectionTestUtils.setField(controller, "telegramService", telegramService);
        ReflectionTestUtils.setField(controller, "telegramBotHandler", handler);

        ResponseEntity<Void> resp = controller.webhook("wrong", Map.of("update_id", 1));
        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
        verify(handler, never()).handleUpdate(any());
    }

    @Test
    void webhook_acceptsMd5AccessToken() {
        TelegramService telegramService = mock(TelegramService.class);
        TelegramBotHandler handler = mock(TelegramBotHandler.class);
        when(telegramService.getBotToken()).thenReturn("secret-bot-token");
        String token = TelegramService.md5Hex("secret-bot-token");

        GuestTelegramController controller = new GuestTelegramController();
        ReflectionTestUtils.setField(controller, "telegramService", telegramService);
        ReflectionTestUtils.setField(controller, "telegramBotHandler", handler);

        ResponseEntity<Void> resp = controller.webhook(token, Map.of("update_id", 1));
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        verify(handler).handleUpdate(any());
    }
}
