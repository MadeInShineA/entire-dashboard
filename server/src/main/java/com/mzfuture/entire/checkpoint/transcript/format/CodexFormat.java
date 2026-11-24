package com.mzfuture.entire.checkpoint.transcript.format;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.mzfuture.entire.checkpoint.dto.response.NormalizedTranscriptDTO;
import com.mzfuture.entire.checkpoint.dto.response.NormalizedTranscriptMetaDTO;
import com.mzfuture.entire.checkpoint.dto.response.TranscriptFileChangeSummaryDTO;
import com.mzfuture.entire.checkpoint.dto.response.TranscriptMessageViewDTO;
import com.mzfuture.entire.checkpoint.dto.response.TranscriptToolUseDTO;
import com.mzfuture.entire.checkpoint.transcript.TranscriptFormat;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Codex NDJSON (rollout files): each line is `{timestamp, type, payload}`.
///
/// Codex logs most things twice: `response_item` carries the model-facing items (messages, tool
/// calls, outputs, reasoning) and `event_msg` carries UI events (`user_message`, `agent_message`,
/// `exec_command_end`, ...) that duplicate them. Assistant text and tools come from
/// `response_item`; the user's prompt comes from `event_msg.user_message`, because the
/// `response_item` user messages also contain injected context (AGENTS.md, environment, ...).
/// Either side falls back to the other when absent, so older rollouts still render.
///
/// Tools are normalized so the UI renders them like Claude Code's:
/// - shell tools (`shell`, `exec_command`, `local_shell`, ...) get a string `command`;
/// - `apply_patch` is split into one tool per file (`path`, `operation`, `patch`), which gives
///   inline diffs, file-path links and git-backed full-file diffs for free;
/// - outputs are unwrapped from Codex's `{output, metadata}` JSON and the exit-code banner, and
///   a non-zero exit marks the tool as failed.
@Component
public class CodexFormat implements TranscriptFormat {

    private static final String NAME = "codex-ndjson";
    private static final Pattern PATCH_FILE_HEADER =
            Pattern.compile("^\\*\\*\\* (Add|Update|Delete) File: (.+)$");
    private static final Pattern PATCH_MOVE_TO = Pattern.compile("^\\*\\*\\* Move to: (.+)$");
    private static final Pattern EXIT_CODE =
            Pattern.compile("(?:Process exited with code|Exit code:)\\s*(-?\\d+)");
    /// Context Codex injects as "user" messages; never something the user typed. Tags from
    /// codex-rs/protocol (`*_OPEN_TAG` constants) plus AGENTS.md / older-version wrappers.
    private static final String[] INJECTED_PREFIXES = {
            "<environment_context", "<user_instructions", "<environments_instructions",
            "<apps_instructions", "<catalog_apps_instructions", "<skills_instructions",
            "<plugins_instructions", "<tools>", "<collaboration_mode", "<multi_agent_mode",
            "<realtime_conversation", "<context_window", "<permissions", "<user_shell_command",
            "<turn_aborted", "# AGENTS.md", "<INSTRUCTIONS>",
    };
    /// Older Codex versions prepend context and put the real prompt after this marker.
    private static final String USER_MESSAGE_BEGIN = "## My request for Codex:";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override public String name() { return NAME; }
    @Override public int priority() { return 60; }

    @Override
    public boolean matches(String raw) {
        for (String line : raw.split("\\R")) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            try {
                JsonNode n = objectMapper.readTree(t);
                if (!n.isObject() || !n.has("type") || !n.has("payload")) continue;
                String type = n.get("type").asText();
                if (type.equals("session_meta") || type.equals("response_item")
                        || type.equals("event_msg") || type.equals("turn_context")) {
                    return true;
                }
            } catch (Exception e) {
                continue;
            }
        }
        return false;
    }

    @Override
    public NormalizedTranscriptDTO parse(String raw, long rawBytesLength, List<String> warnings) throws Exception {
        List<JsonNode> lines = new ArrayList<>();
        for (String rawLine : raw.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;
            try {
                JsonNode n = objectMapper.readTree(line);
                if (n.path("payload").isObject()) lines.add(n);
            } catch (Exception e) {
                // skip malformed line
            }
        }

        boolean hasEventUser = false;
        boolean hasItemAssistant = false;
        String projectRoot = null;
        for (JsonNode n : lines) {
            String type = textOrNull(n, "type");
            JsonNode p = n.path("payload");
            String ptype = textOrNull(p, "type");
            if ("event_msg".equals(type) && "user_message".equals(ptype)) hasEventUser = true;
            if ("response_item".equals(type) && "message".equals(ptype)
                    && "assistant".equals(textOrNull(p, "role"))) hasItemAssistant = true;
            if (projectRoot == null && ("session_meta".equals(type) || "turn_context".equals(type))) {
                String cwd = textOrNull(p, "cwd");
                if (cwd != null && !cwd.isEmpty()) projectRoot = cwd;
            }
        }

        State s = new State(projectRoot);
        for (JsonNode n : lines) {
            String type = textOrNull(n, "type");
            JsonNode p = n.path("payload");
            String ptype = textOrNull(p, "type");
            if (ptype == null) continue;
            if ("response_item".equals(type)) {
                switch (ptype) {
                    case "message" -> {
                        String role = textOrNull(p, "role");
                        if ("assistant".equals(role)) {
                            addMessage(s, "assistant", contentText(p.path("content"), false));
                        } else if ("user".equals(role) && !hasEventUser) {
                            addMessage(s, "user", contentText(p.path("content"), true));
                        }
                        // developer / system messages are prompt plumbing: dropped
                    }
                    case "reasoning" -> appendReasoning(s, reasoningText(p));
                    case "function_call", "custom_tool_call", "local_shell_call" -> handleToolCall(s, p, ptype);
                    case "function_call_output", "custom_tool_call_output" -> handleToolOutput(s, p);
                    default -> { /* web_search_call etc.: dropped */ }
                }
            } else if ("event_msg".equals(type)) {
                switch (ptype) {
                    case "user_message" -> addMessage(s, "user", stripInjected(textOrNull(p, "message")));
                    case "agent_message" -> {
                        if (!hasItemAssistant) addMessage(s, "assistant", textOrNull(p, "message"));
                    }
                    case "exec_command_end" -> handleExecEnd(s, p);
                    case "patch_apply_end" -> handlePatchEnd(s, p);
                    default -> { /* token_count, task_*, agent_reasoning (dup of reasoning): dropped */ }
                }
            }
        }

        int stepsCount = 0;
        for (TranscriptMessageViewDTO mv : s.messages) {
            if ("user".equals(mv.getRole())) stepsCount++;
        }

        List<TranscriptFileChangeSummaryDTO> fileChanges = new ArrayList<>();
        for (Map.Entry<String, int[]> e : s.fileChanges.entrySet()) {
            TranscriptFileChangeSummaryDTO fc = new TranscriptFileChangeSummaryDTO();
            fc.setFile(e.getKey());
            fc.setAdditions(e.getValue()[0]);
            fc.setDeletions(e.getValue()[1]);
            fileChanges.add(fc);
        }

        NormalizedTranscriptDTO dto = new NormalizedTranscriptDTO();
        dto.setSchemaVersion(1);
        NormalizedTranscriptMetaDTO meta = new NormalizedTranscriptMetaDTO();
        meta.setSourceFormat(NAME);
        meta.setRawBytesLength(rawBytesLength);
        if (!warnings.isEmpty()) meta.setWarnings(new ArrayList<>(warnings));
        dto.setMeta(meta);
        dto.setProjectRoot(projectRoot);
        dto.setStepsCount(stepsCount);
        dto.setFileChanges(fileChanges);
        dto.setMessages(s.messages);
        return dto;
    }

    // ---------------------------------------------------------------- messages

    private static final class State {
        final String projectRoot;
        final List<TranscriptMessageViewDTO> messages = new ArrayList<>();
        /// call_id -> tool DTOs it produced (apply_patch yields one per file)
        final Map<String, List<TranscriptToolUseDTO>> toolsByCallId = new HashMap<>();
        final Map<String, int[]> fileChanges = new LinkedHashMap<>();
        /// Reasoning summary seen before the assistant message it belongs to
        String pendingReasoning;

        State(String projectRoot) { this.projectRoot = projectRoot; }
    }

    private void addMessage(State s, String role, String text) {
        if (text == null || text.isBlank()) return;
        TranscriptMessageViewDTO mv = new TranscriptMessageViewDTO();
        mv.setRole(role);
        mv.setText(text.trim());
        if ("assistant".equals(role) && s.pendingReasoning != null) {
            mv.setReasoning(s.pendingReasoning);
            s.pendingReasoning = null;
        }
        s.messages.add(mv);
    }

    private void appendReasoning(State s, String text) {
        if (text == null || text.isBlank()) return;
        s.pendingReasoning = s.pendingReasoning == null ? text : s.pendingReasoning + "\n\n" + text;
    }

    /// The assistant message tools attach to: the last one, or a fresh empty one after a user turn.
    private TranscriptMessageViewDTO currentAssistant(State s) {
        if (!s.messages.isEmpty()) {
            TranscriptMessageViewDTO last = s.messages.get(s.messages.size() - 1);
            if ("assistant".equals(last.getRole())) {
                if (s.pendingReasoning != null) {
                    last.setReasoning(last.getReasoning() == null
                            ? s.pendingReasoning : last.getReasoning() + "\n\n" + s.pendingReasoning);
                    s.pendingReasoning = null;
                }
                return last;
            }
        }
        TranscriptMessageViewDTO mv = new TranscriptMessageViewDTO();
        mv.setRole("assistant");
        mv.setText("");
        mv.setReasoning(s.pendingReasoning);
        s.pendingReasoning = null;
        s.messages.add(mv);
        return mv;
    }

    private static String contentText(JsonNode content, boolean userSide) {
        if (content.isTextual()) return userSide ? stripInjected(content.asText()) : content.asText();
        if (!content.isArray()) return "";
        StringBuilder sb = new StringBuilder();
        for (JsonNode block : content) {
            String bt = textOrNull(block, "type");
            if (!"text".equals(bt) && !"input_text".equals(bt) && !"output_text".equals(bt)) continue;
            String t = block.path("text").asText("");
            if (userSide) t = stripInjected(t);
            if (t == null || t.isBlank()) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(t);
        }
        return sb.toString().trim();
    }

    private static String stripInjected(String text) {
        if (text == null) return null;
        String t = text.trim();
        int marker = t.lastIndexOf(USER_MESSAGE_BEGIN);
        if (marker >= 0) return t.substring(marker + USER_MESSAGE_BEGIN.length()).trim();
        for (String prefix : INJECTED_PREFIXES) {
            if (t.startsWith(prefix)) return "";
        }
        return t;
    }

    private static String reasoningText(JsonNode p) {
        StringBuilder sb = new StringBuilder();
        for (String field : new String[]{"summary", "content"}) {
            JsonNode arr = p.path(field);
            if (!arr.isArray()) continue;
            for (JsonNode b : arr) {
                String t = b.isTextual() ? b.asText() : b.path("text").asText("");
                if (t.isBlank()) continue;
                if (sb.length() > 0) sb.append("\n\n");
                sb.append(t.trim());
            }
            if (sb.length() > 0) break; // prefer the summary; raw content only when no summary
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    // ---------------------------------------------------------------- tools

    private void handleToolCall(State s, JsonNode p, String ptype) {
        String callId = firstNonNull(textOrNull(p, "call_id"), textOrNull(p, "id"), "");
        String name = firstNonNull(textOrNull(p, "name"), "local_shell_call".equals(ptype) ? "shell" : null, "");

        // Arguments: JSON string (function_call), raw text (custom_tool_call `input`), or object.
        JsonNode args;
        String argText = null;
        if ("local_shell_call".equals(ptype)) {
            args = p.path("action");
        } else {
            JsonNode a = p.has("arguments") ? p.get("arguments") : p.path("input");
            if (a.isTextual()) {
                argText = a.asText("");
                args = tryParse(argText);
            } else {
                args = a;
            }
        }

        List<TranscriptToolUseDTO> produced = new ArrayList<>();
        String patch = patchText(name, args, argText);
        if (patch != null) {
            produced.addAll(splitPatch(s, callId, patch));
        }
        if (produced.isEmpty()) {
            TranscriptToolUseDTO tu = new TranscriptToolUseDTO();
            tu.setCallID(callId);
            tu.setTool(name);
            tu.setInput(normalizeInput(args, argText));
            produced.add(tu);
        }

        TranscriptMessageViewDTO target = currentAssistant(s);
        if (target.getTools() == null) target.setTools(new ArrayList<>());
        target.getTools().addAll(produced);
        target.setToolsCount(target.getTools().size());
        if (!callId.isEmpty()) s.toolsByCallId.put(callId, produced);
    }

    /// The patch body when this call applies a patch: the `apply_patch` tool itself, or a shell
    /// call running `apply_patch <<'EOF' ...` (how older Codex versions edited files).
    private static String patchText(String name, JsonNode args, String argText) {
        if ("apply_patch".equals(name)) {
            if (args != null && args.isObject()) {
                for (String k : new String[]{"input", "patch"}) {
                    if (args.path(k).isTextual()) return args.get(k).asText();
                }
            }
            return argText;
        }
        String cmd = commandString(args);
        if (cmd != null && cmd.contains("*** Begin Patch") && cmd.contains("apply_patch")) {
            return cmd;
        }
        return null;
    }

    /// One `apply_patch` tool per file section of the patch.
    private List<TranscriptToolUseDTO> splitPatch(State s, String callId, String patch) {
        List<TranscriptToolUseDTO> out = new ArrayList<>();
        String[] lines = patch.split("\\R", -1);
        int i = 0;
        while (i < lines.length) {
            Matcher h = PATCH_FILE_HEADER.matcher(lines[i]);
            if (!h.matches()) { i++; continue; }
            String op = h.group(1).toLowerCase();
            String path = h.group(2).trim();
            String moveTo = null;
            StringBuilder body = new StringBuilder();
            int adds = 0, dels = 0;
            i++;
            for (; i < lines.length; i++) {
                String l = lines[i];
                if (PATCH_FILE_HEADER.matcher(l).matches() || l.startsWith("*** End Patch")) break;
                if (l.matches("['\"]?EOF['\"]?")) break; // heredoc terminator of `apply_patch <<'EOF'`
                if (l.startsWith("*** ")) continue;     // e.g. "*** Environment ID: ..."
                Matcher mv = PATCH_MOVE_TO.matcher(l);
                if (mv.matches()) { moveTo = mv.group(1).trim(); continue; }
                if (l.startsWith("*** End of File")) continue;
                if (l.startsWith("+")) adds++;
                else if (l.startsWith("-")) dels++;
                body.append(l).append('\n');
            }

            String filePath = moveTo != null ? moveTo : path;
            ObjectNode input = objectMapper.createObjectNode();
            input.put("path", filePath);
            input.put("operation", op);
            if (moveTo != null) input.put("move_from", path);
            input.put("patch", body.toString());

            TranscriptToolUseDTO tu = new TranscriptToolUseDTO();
            tu.setCallID(out.isEmpty() ? callId : callId + "#" + out.size());
            tu.setTool("apply_patch");
            tu.setInput(input);
            out.add(tu);

            int fa = adds, fd = dels;
            s.fileChanges.compute(relativize(filePath, s.projectRoot), (k, v) -> {
                if (v == null) return new int[]{fa, fd};
                v[0] += fa; v[1] += fd;
                return v;
            });
        }
        return out;
    }

    /// Shell-like inputs get a plain string `command` (the UI shows it as-is); other fields kept.
    private JsonNode normalizeInput(JsonNode args, String argText) {
        if (args == null || !args.isObject()) {
            return argText != null ? TextNode.valueOf(argText) : args;
        }
        String cmd = commandString(args);
        if (cmd == null) return args;
        ObjectNode copy = ((ObjectNode) args).deepCopy();
        copy.remove("cmd");
        copy.put("command", cmd);
        return copy;
    }

    /// `cmd` (exec_command) or `command` as a string or argv array; `bash -lc <script>` unwrapped.
    private static String commandString(JsonNode args) {
        if (args == null || !args.isObject()) return null;
        JsonNode c = args.has("cmd") ? args.get("cmd") : args.get("command");
        if (c == null) return null;
        if (c.isTextual()) return c.asText();
        if (!c.isArray() || c.isEmpty()) return null;
        List<String> argv = new ArrayList<>();
        for (JsonNode a : c) argv.add(a.asText(""));
        if (argv.size() == 3 && (argv.get(1).equals("-lc") || argv.get(1).equals("-c"))) {
            return argv.get(2);
        }
        return String.join(" ", argv);
    }

    private void handleToolOutput(State s, JsonNode p) {
        String callId = textOrNull(p, "call_id");
        List<TranscriptToolUseDTO> tools = callId == null ? null : s.toolsByCallId.get(callId);
        if (tools == null) return;

        JsonNode out = p.path("output");
        String text;
        Integer exitCode = null;
        if (out.isTextual()) {
            text = out.asText("");
            JsonNode parsed = text.startsWith("{") ? tryParse(text) : null;
            if (parsed != null && parsed.isObject() && parsed.has("output")) {
                text = parsed.path("output").asText("");
                JsonNode ec = parsed.path("metadata").path("exit_code");
                if (ec.isNumber()) exitCode = ec.asInt();
            }
        } else if (out.isArray()) {
            // FunctionCallOutputBody::ContentItems: [{type: input_text, text}, {type: input_image, ...}]
            StringBuilder sb = new StringBuilder();
            for (JsonNode item : out) {
                String t = item.path("text").asText("");
                if (t.isEmpty()) continue;
                if (sb.length() > 0) sb.append('\n');
                sb.append(t);
            }
            text = sb.toString();
        } else if (out.isObject() && out.has("content")) {
            text = out.path("content").asText("");
            if (out.path("success").isBoolean() && !out.get("success").asBoolean()) exitCode = 1;
        } else if (out.isMissingNode() || out.isNull()) {
            return;
        } else {
            text = out.toString();
        }

        // exec_command: "Chunk ID: ...\nWall time: ...\nProcess exited with code N\n...\nOutput:\n<body>"
        Matcher ec = EXIT_CODE.matcher(text.length() > 400 ? text.substring(0, 400) : text);
        if (exitCode == null && ec.find()) exitCode = Integer.parseInt(ec.group(1));
        int body = text.indexOf("\nOutput:\n");
        if (body >= 0 && body < 400) text = text.substring(body + "\nOutput:\n".length());
        else if (text.startsWith("Output:\n")) text = text.substring("Output:\n".length());

        if (exitCode != null && exitCode != 0 && !text.contains("exit code")) {
            text = "Exit code " + exitCode + (text.isBlank() ? "" : "\n" + text);
        }
        for (TranscriptToolUseDTO tu : tools) {
            tu.setOutput(TextNode.valueOf(text.stripTrailing()));
            if (exitCode != null && exitCode != 0) tu.setError(true);
        }
    }

    /// `exec_command_end` carries the authoritative exit code; use it when the output had none.
    private void handleExecEnd(State s, JsonNode p) {
        List<TranscriptToolUseDTO> tools = s.toolsByCallId.get(textOrNull(p, "call_id"));
        JsonNode ec = p.path("exit_code");
        if (tools == null || !ec.isNumber() || ec.asInt() == 0) return;
        for (TranscriptToolUseDTO tu : tools) tu.setError(true);
    }

    private void handlePatchEnd(State s, JsonNode p) {
        List<TranscriptToolUseDTO> tools = s.toolsByCallId.get(textOrNull(p, "call_id"));
        JsonNode ok = p.path("success");
        if (tools == null || !ok.isBoolean() || ok.asBoolean()) return;
        for (TranscriptToolUseDTO tu : tools) tu.setError(true);
    }

    // ---------------------------------------------------------------- helpers

    private JsonNode tryParse(String text) {
        try {
            return objectMapper.readTree(text);
        } catch (Exception e) {
            return null;
        }
    }

    private static String relativize(String path, String root) {
        String p = path.replace('\\', '/');
        if (root == null || root.isEmpty()) return p;
        String r = root.replace('\\', '/').replaceAll("/$", "");
        return p.startsWith(r + "/") ? p.substring(r.length() + 1) : p;
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T v : values) if (v != null) return v;
        return null;
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) return null;
        JsonNode v = node.get(field);
        return v.isValueNode() ? v.asText() : null;
    }
}
