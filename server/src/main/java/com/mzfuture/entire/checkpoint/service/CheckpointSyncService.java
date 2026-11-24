package com.mzfuture.entire.checkpoint.service;

/// Orchestrates checkpoint sync: branch check, clone/pull, commit walk, parse, upsert.
public interface CheckpointSyncService {

    /// Sync one repo: clone/pull entire/checkpoints/v1, walk commits, upsert checkpoints.
    /// Throws an AppException on any failure, including when the remote has no checkpoint data
    /// (NO_CHECKPOINTS) or rejects the token (GIT_REMOTE_ERROR); returning normally means the sync succeeded.
    /// @param repoId repository id
    /// @param fullScan if true, ignore last_processed and walk all commits from HEAD to root
    void syncRepo(Long repoId, boolean fullScan);

    /// Sync all repos (syncRepo for each; a failing repo is logged and the others still run).
    /// @param fullScan if true, full scan for each repo
    void syncAllRepos(boolean fullScan);
}
