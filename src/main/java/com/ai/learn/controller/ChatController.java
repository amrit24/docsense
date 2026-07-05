package com.ai.learn.controller;

import com.ai.learn.model.ChatRequest;
import com.ai.learn.model.ChatResponse;
import com.ai.learn.service.RagQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@Tag(name = "Chat", description = "Ask natural-language questions about uploaded PDF documents")
public class ChatController {

    private final RagQueryService ragQueryService;

    @Operation(
        summary = "Ask a question",
        description = """
            Embeds the question, retrieves the most relevant chunks from ChromaDB,
            builds a grounded prompt, and generates an answer using the local Ollama model.
            The answer is sourced strictly from the uploaded documents — no general knowledge is used.
            """,
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ChatRequest.class),
                examples = {
                    @ExampleObject(
                        name = "Search all documents",
                        value = """
                            {
                              "question": "Explain the Singleton design pattern."
                            }
                            """
                    ),
                    @ExampleObject(
                        name = "Scope to one document",
                        value = """
                            {
                              "question": "What are the key principles discussed in chapter 3?",
                              "documentId": "a3f1c2d4-7e89-4b3a-bf12-3c9f01234567"
                            }
                            """
                    )
                }
            )
        ),
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "Answer generated successfully with source citations",
                content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ChatResponse.class),
                    examples = @ExampleObject(value = """
                        {
                          "answer": "The Singleton pattern ensures that only one instance of a class exists and provides a global access point to it. It is commonly implemented using a private constructor and a static method that returns the single instance.",
                          "sources": [
                            { "page": 22, "file": "JavaDesignPatterns.pdf" },
                            { "page": 23, "file": "JavaDesignPatterns.pdf" }
                          ]
                        }
                        """)
                )
            ),
            @ApiResponse(responseCode = "400", description = "Question is blank or request body is invalid")
        }
    )
    @PostMapping
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        log.info("Chat request: \"{}\"", request.getQuestion());
        ChatResponse response = ragQueryService.chat(request);
        return ResponseEntity.ok(response);
    }
}
