package com.mzfuture.entire.overview.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/// One checkpoint with the sums of its sessions, the unit the dashboard aggregates client-side
@Data
@Schema(description = "Checkpoint with aggregated session metrics")
public class OverviewAnalyticsRowDTO {

    @Schema(description = "Primary key ID", example = "1")
    private Long id;

    @Schema(description = "12-digit hex checkpoint identifier", example = "a1b2c3d4e5f6")
    private String checkpointId;

    @Schema(description = "Repository ID", example = "1")
    private Long repoId;

    @Schema(description = "Repository name", example = "my-repo")
    private String repoName;

    @Schema(description = "Git branch name", example = "main")
    private String branch;

    @Schema(description = "Git commit SHA")
    private String commitSha;

    @Schema(description = "First line of the commit message, at most 200 characters")
    private String commitMessage;

    @Schema(description = "Git commit author display name")
    private String commitAuthorName;

    @Schema(description = "Git commit time (Unix epoch ms)")
    private Long commitTime;

    @Schema(description = "AI agent name", example = "Claude Code")
    private String agent;

    @Schema(description = "Number of files touched")
    private Integer filesTouched;

    @Schema(description = "Lines added in the commit")
    private Integer additions;

    @Schema(description = "Lines deleted in the commit")
    private Integer deletions;

    @Schema(description = "Cumulative token usage recorded on the checkpoint")
    private Long tokenUsage;

    @Schema(description = "Number of sessions of the checkpoint")
    private Integer sessionCount;

    @Schema(description = "Sum of session input tokens")
    private Long inputTokens;

    @Schema(description = "Sum of session output tokens")
    private Long outputTokens;

    @Schema(description = "Sum of session API calls")
    private Long apiCallCount;

    @Schema(description = "Sum of session prompt-cache write tokens")
    private Long cacheCreationTokens;

    @Schema(description = "Sum of session prompt-cache read tokens")
    private Long cacheReadTokens;

    @Schema(description = "Sum of session turns (prompts)")
    private Long turnCount;

    @Schema(description = "Model used by the agent", example = "claude-opus-5-5")
    private String model;
}
