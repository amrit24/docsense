package com.docsense.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@Schema(description = "AI-generated answer grounded in the uploaded documents, with source citations")
public class ChatResponse {

    @Schema(
        description = "AI-generated answer based strictly on retrieved document context",
        example = "The Singleton pattern ensures that only one instance of a class exists and provides a global access point to it."
    )
    private String answer;

    @Schema(description = "Deduplicated list of source pages used to generate the answer, ordered by relevance")
    private List<Source> sources;
}
