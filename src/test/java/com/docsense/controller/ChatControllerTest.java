package com.docsense.controller;

import com.docsense.model.ChatRequest;
import com.docsense.model.ChatResponse;
import com.docsense.service.RagQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.springframework.http.MediaType;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    MockMvc mvc;

    @Mock
    RagQueryService ragQueryService;

    @InjectMocks
    ChatController chatController;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(chatController).build();
    }

    @Test
    void chat_endpoint_returns_answer() throws Exception {
        ChatResponse resp = ChatResponse.builder().answer("ok").build();
        when(ragQueryService.chat(any(ChatRequest.class))).thenReturn(resp);

        mvc.perform(post("/api/v1/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"Hi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("ok"));
    }
}
