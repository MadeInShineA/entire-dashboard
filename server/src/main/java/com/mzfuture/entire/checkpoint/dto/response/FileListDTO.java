package com.mzfuture.entire.checkpoint.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Files of a repository branch, with how many AI sessions touched each")
public class FileListDTO {

    @Schema(description = "Local content branches available for this repository")
    private List<String> branches;

    @Schema(description = "Branch the file list was read from")
    private String branch;

    @Schema(description = "Files at the branch head, plus files touched by sessions that no longer exist there")
    private List<FileEntryDTO> files;

    @Data
    @Schema(description = "One file entry")
    public static class FileEntryDTO {

        @Schema(description = "Repo-relative path")
        private String path;

        @Schema(description = "Number of AI sessions whose files_touched includes this path")
        private int sessionCount;

        @Schema(description = "Whether the file exists at the branch head")
        private boolean existsAtHead;
    }
}
