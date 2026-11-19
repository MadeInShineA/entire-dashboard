package com.mzfuture.entire.checkpoint.controller;

import com.mzfuture.entire.checkpoint.dto.response.FileHistoryDTO;
import com.mzfuture.entire.checkpoint.dto.response.FileListDTO;
import com.mzfuture.entire.checkpoint.service.FileHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/// File-centric APIs: which sessions touched a file and line-by-line attribution
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/file")
@Tag(name = "File history", description = "Sessions per file and line-by-line attribution")
public class FileHistoryController {

    private final FileHistoryService fileHistoryService;

    @GetMapping("/list")
    @Operation(summary = "List files", description = "Files at the branch head with the number of AI sessions that touched each. branch defaults to main/master.")
    public FileListDTO list(@RequestParam Long repoId, @RequestParam(required = false) String branch) {
        return fileHistoryService.listFiles(repoId, branch);
    }

    @GetMapping("/history")
    @Operation(summary = "File history", description = "Sessions that touched the file and, for every current line, the checkpoint and session it comes from.")
    public FileHistoryDTO history(@RequestParam Long repoId,
                                  @RequestParam String path,
                                  @RequestParam(required = false) String branch) {
        return fileHistoryService.getFileHistory(repoId, branch, path);
    }
}
