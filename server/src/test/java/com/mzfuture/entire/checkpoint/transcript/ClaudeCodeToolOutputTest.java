package com.mzfuture.entire.checkpoint.transcript;

import com.mzfuture.entire.checkpoint.dto.response.NormalizedTranscriptDTO;
import com.mzfuture.entire.checkpoint.dto.response.TranscriptMessageViewDTO;
import com.mzfuture.entire.checkpoint.dto.response.TranscriptToolUseDTO;
import com.mzfuture.entire.checkpoint.transcript.format.ClaudeCodeFormat;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Tool results are attached to their tool call, and tool input/output serialize as plain JSON
/// with Jackson 3 (what Spring Boot 4 uses for HTTP responses).
class ClaudeCodeToolOutputTest {

    private static final String TRANSCRIPT = String.join("\n",
            "{\"type\":\"user\",\"cwd\":\"/repo\",\"message\":{\"role\":\"user\",\"content\":\"List the files\"}}",
            "{\"type\":\"assistant\",\"message\":{\"id\":\"m1\",\"role\":\"assistant\",\"content\":["
                    + "{\"type\":\"text\",\"text\":\"Listing.\"},"
                    + "{\"type\":\"tool_use\",\"id\":\"t1\",\"name\":\"Bash\",\"input\":{\"command\":\"ls -la\",\"description\":\"List files\"}},"
                    + "{\"type\":\"tool_use\",\"id\":\"t2\",\"name\":\"Read\",\"input\":{\"file_path\":\"/repo/missing.txt\"}}]}}",
            "{\"type\":\"user\",\"message\":{\"role\":\"user\",\"content\":["
                    + "{\"type\":\"tool_result\",\"tool_use_id\":\"t1\",\"content\":[{\"type\":\"text\",\"text\":\"a.txt\\nb.txt\"}]},"
                    + "{\"type\":\"tool_result\",\"tool_use_id\":\"t2\",\"is_error\":true,\"content\":\"File does not exist.\"}]}}",
            "{\"type\":\"assistant\",\"message\":{\"id\":\"m2\",\"role\":\"assistant\",\"content\":[{\"type\":\"text\",\"text\":\"Done.\"}]}}");

    @Test
    void attachesToolResultsAndSerializesPlainJson() throws Exception {
        NormalizedTranscriptDTO dto = new ClaudeCodeFormat().parse(TRANSCRIPT, TRANSCRIPT.length(), new ArrayList<>());

        // The tool_result-only user line is folded into the tools, not shown as an empty message
        List<String> roles = dto.getMessages().stream().map(TranscriptMessageViewDTO::getRole).toList();
        assertEquals(List.of("user", "assistant", "assistant"), roles);
        assertEquals(1, dto.getStepsCount());

        List<TranscriptToolUseDTO> tools = dto.getMessages().get(1).getTools();
        assertEquals("a.txt\nb.txt", tools.get(0).getOutput().asText());
        assertNull(tools.get(0).getError());
        assertEquals("File does not exist.", tools.get(1).getOutput().asText());
        assertTrue(tools.get(1).getError());

        String json = JsonMapper.builder().build().writeValueAsString(tools.get(0));
        assertTrue(json.contains("\"input\":{\"command\":\"ls -la\",\"description\":\"List files\"}"), json);
        assertTrue(json.contains("\"output\":\"a.txt\\nb.txt\""), json);
        assertFalse(json.contains("nodeType"), json);
        assertFalse(json.contains("inputJson"), json);
    }
}
