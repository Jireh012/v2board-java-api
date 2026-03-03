package com.v2board.api.controller;

import com.v2board.api.V2boardApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = V2boardApplication.class)
@AutoConfigureMockMvc
public class UserAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void checkLogin_shouldReturnOk() throws Exception {
        mockMvc.perform(post("/api/v1/user/checkLogin"))
                .andExpect(status().isOk());
    }
}

