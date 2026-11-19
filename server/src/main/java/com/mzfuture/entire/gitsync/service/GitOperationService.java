package com.mzfuture.entire.gitsync.service;

import com.mzfuture.entire.gitsync.dto.response.GitStatusDTO;
import com.mzfuture.entire.gitsync.dto.response.PullResult;

/// Git operation service interface
/// Provides basic Git operation functions (clone, pull, status, etc.)
public interface GitOperationService {

    /// Clone repository (single branch shallow clone)
    ///
    /// @param repoId Repository ID
    /// @param authUrl URL with authentication
    /// @param branch Branch name
    /// @return Local repository path
    String cloneRepository(Long repoId, String authUrl, String branch);

    /// Clone repository (depth can be specified, depth<0 means use global default depth, 0 means full history)
    ///
    /// @param repoId Repository ID
    /// @param authUrl URL with authentication
    /// @param branch Branch name
    /// @param depth Clone depth, 0=full, >0=shallow clone depth
    /// @return Local repository path
    String cloneRepository(Long repoId, String authUrl, String branch, int depth);

    /// Pull latest code
    ///
    /// @param repoId Repository ID
    /// @param authUrl URL with authentication
    /// @param branch Branch name
    /// @return Pull operation result
    PullResult pullRepository(Long repoId, String authUrl, String branch);

    /// Get repository current status
    ///
    /// @param repoId Repository ID
    /// @return Repository status information
    GitStatusDTO getRepositoryStatus(Long repoId);

    /// Check if local repository exists
    ///
    /// @param repoId Repository ID
    /// @return Whether exists
    boolean isRepositoryExists(Long repoId);

    /// Get local repository path
    ///
    /// @param repoId Repository ID
    /// @return Local path
    String getLocalRepositoryPath(Long repoId);

    /// Delete local repository
    ///
    /// @param repoId Repository ID
    /// @return Whether successfully deleted
    boolean deleteRepository(Long repoId);

    /// Whether the local clone is shallow (has a .git/shallow file)
    ///
    /// @param repoId Repository ID
    /// @return true if the local repository exists and is shallow
    boolean isShallow(Long repoId);

    /// Get all branch names from remote repository
    ///
    /// @param authUrl URL with authentication
    /// @return List of branch names
    java.util.List<String> listRemoteBranches(String authUrl);

    /// Fetch specified branches on existing local repository (without checkout switch), used for checkpoint multi-branch sync
    ///
    /// @param repoId Repository ID
    /// @param authUrl URL with authentication
    /// @param branchNames List of branch names to fetch
    void fetchBranches(Long repoId, String authUrl, java.util.List<String> branchNames);

    /// Fetch arbitrary refspecs on an existing local repository
    /// (e.g. "+refs/entire/checkpoints/*:refs/entire/checkpoints/*")
    ///
    /// @param repoId Repository ID
    /// @param authUrl URL with authentication
    /// @param refSpecs Refspecs to fetch
    void fetchRefSpecs(Long repoId, String authUrl, java.util.List<String> refSpecs);

    /// List full ref names on the remote starting with the given prefix (e.g. "refs/entire/checkpoints/")
    ///
    /// @param authUrl URL with authentication
    /// @param prefix Ref name prefix
    /// @return Full ref names
    java.util.List<String> listRemoteRefs(String authUrl, String prefix);
}
