package com.docsense.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "A source reference pointing to the exact page in a PDF that supported the answer")
public class Source {

    @Schema(description = "Page number in the source PDF (1-based)", example = "22")
    private int page;

    @Schema(description = "Filename of the source PDF", example = "JavaDesignPatterns.pdf")
    private String file;
}
