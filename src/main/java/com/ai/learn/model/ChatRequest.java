package com.ai.learn.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Request body for asking a question about uploaded documents")
public class ChatRequest {

    @NotBlank(message = "Question must not be blank")
    @Schema(
        description = "Natural-language question about the uploaded documents",
        example = "Explain the Singleton design pattern.",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String question;

    @Schema(
        description = "Optional UUID of a specific document to scope the search to. " +
                      "When omitted, all indexed documents are searched. " +
                      "Obtained from the upload response.",
        example = "a3f1c2d4-7e89-4b3a-bf12-3c9f01234567"
    )
    private String documentId;
}
