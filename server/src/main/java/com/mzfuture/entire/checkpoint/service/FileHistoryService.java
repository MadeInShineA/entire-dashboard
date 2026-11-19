package com.mzfuture.entire.checkpoint.service;

import com.mzfuture.entire.checkpoint.dto.response.FileHistoryDTO;
import com.mzfuture.entire.checkpoint.dto.response.FileListDTO;

public interface FileHistoryService {

    /// Files of a branch head (plus files touched by sessions but no longer present), with session counts
    FileListDTO listFiles(Long repoId, String branch);

    /// Sessions that touched a file and per-line checkpoint/session attribution of its current content
    FileHistoryDTO getFileHistory(Long repoId, String branch, String path);
}
