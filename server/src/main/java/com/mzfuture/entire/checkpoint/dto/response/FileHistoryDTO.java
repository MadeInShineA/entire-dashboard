package com.mzfuture.entire.checkpoint.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Which AI sessions touched a file, and line-by-line attribution of its current content")
public class FileHistoryDTO {

    @Schema(description = "Repo-relative path")
    private String path;

    @Schema(description = "Branch the file was read from")
    private String branch;

    @Schema(description = "Commit SHA of the branch head")
    private String headSha;

    @Schema(description = "Whether the file exists at the branch head")
    private boolean existsAtHead;

    @Schema(description = "Binary file: no line attribution")
    private boolean binary;

    @Schema(description = "Line list was truncated")
    private boolean truncated;

    @Schema(description = "Number of lines in the current version")
    private int totalLines;

    @Schema(description = "Lines whose last change came from a commit linked to a checkpoint")
    private int checkpointLines;

    @Schema(description = "Sessions that touched the file, newest commit first")
    private List<FileSessionDTO> sessions;

    @Schema(description = "Current content with per-line attribution")
    private List<FileLineDTO> lines;

    @Data
    @Schema(description = "A session that touched the file")
    public static class FileSessionDTO {

        @Schema(description = "Checkpoint (commit) the session belongs to")
        private CheckpointDTO checkpoint;

        @Schema(description = "The session; null if the checkpoint has no session metadata")
        private SessionDTO session;

        @Schema(description = "Whether the session's files_touched lists this file")
        private boolean touchedFile;

        @Schema(description = "Lines of the current version last changed by this checkpoint's commit and attributed to this session")
        private int currentLines;
    }

    @Data
    @Schema(description = "One line of the current version")
    public static class FileLineDTO {

        @Schema(description = "1-based line number")
        private int number;

        @Schema(description = "Line text")
        private String content;

        @Schema(description = "Commit that last changed this line")
        private String commitSha;

        @Schema(description = "Author of that commit")
        private String authorName;

        @Schema(description = "Commit time (Unix epoch ms)")
        private long commitTime;

        @Schema(description = "Checkpoint.id (DB) linked to that commit, null if the commit has no checkpoint")
        private Long checkpointId;

        @Schema(description = "Session.id (DB) the line is attributed to, null if none")
        private Long sessionId;
    }
}
