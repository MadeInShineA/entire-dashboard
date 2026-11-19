package com.mzfuture.entire.checkpoint.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/// Tool invocation aligned with the admin UI transcript viewer.
///
/// `input` / `output` are kept as Jackson 2 {@link JsonNode} for the parsers, but the HTTP layer
/// serializes with Jackson 3, which does not know Jackson 2 node types and would dump their bean
/// getters (`nodeType`, `containerNode`, ...). The JSON view therefore exposes plain Java values.
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Tool use in a transcript message")
public class TranscriptToolUseDTO {

    private static final ObjectMapper PLAIN = new ObjectMapper();

    @Schema(description = "Tool call id when present")
    private String callID;

    @Schema(description = "Tool name")
    private String tool;

    @JsonIgnore
    private JsonNode input;

    @JsonIgnore
    private JsonNode output;

    @Schema(description = "Tool result reported an error")
    private Boolean error;

    @Schema(description = "Optional short title")
    private String title;

    @JsonProperty("input")
    @Schema(name = "input", description = "Tool input payload")
    public Object getInputJson() {
        return toPlain(input);
    }

    @JsonProperty("output")
    @Schema(name = "output", description = "Tool output when present")
    public Object getOutputJson() {
        return toPlain(output);
    }

    private static Object toPlain(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;
        return PLAIN.convertValue(node, Object.class);
    }
}
