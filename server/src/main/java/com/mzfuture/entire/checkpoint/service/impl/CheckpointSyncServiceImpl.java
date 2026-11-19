package com.mzfuture.entire.checkpoint.service.impl;

import cn.hutool.core.util.StrUtil;
import com.mzfuture.entire.checkpoint.config.CheckpointSyncProperties;
import com.mzfuture.entire.checkpoint.entity.Checkpoint;
import com.mzfuture.entire.checkpoint.entity.CheckpointSyncState;
import com.mzfuture.entire.checkpoint.git.CheckpointGitReader;
import com.mzfuture.entire.checkpoint.entity.Session;
import com.mzfuture.entire.checkpoint.parser.CheckpointMetadataParser;
import com.mzfuture.entire.checkpoint.parser.CheckpointParseResult;
import com.mzfuture.entire.checkpoint.parser.SessionMetadataParser;
import com.mzfuture.entire.checkpoint.parser.SessionParseResult;
import com.mzfuture.entire.checkpoint.repository.CheckpointRepository;
import com.mzfuture.entire.checkpoint.repository.CheckpointSyncStateRepository;
import com.mzfuture.entire.checkpoint.repository.SessionRepository;
import com.mzfuture.entire.checkpoint.service.CheckpointSyncService;
import com.mzfuture.entire.gitrepo.entity.Repo;
import com.mzfuture.entire.gitrepo.repository.RepoRepository;
import com.mzfuture.entire.gitsync.dto.response.GitBranchesDTO;
import com.mzfuture.entire.gitsync.service.GitSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class CheckpointSyncServiceImpl implements CheckpointSyncService {

    /// Legacy CLI ids are 12 hex chars (e.g. c14692dbdbc6); CLI 0.13+ uses 26-char ULIDs (e.g. 01M7TAEQCJ7PEWZ0R86Y40ER61)
    private static final Pattern TRAILER_PATTERN = Pattern.compile("Entire-Checkpoint:\\s*([0-9A-Za-z]{12,32})\\b", Pattern.CASE_INSENSITIVE);

    private final RepoRepository repoRepository;
    private final CheckpointSyncStateRepository syncStateRepository;
    private final CheckpointRepository checkpointRepository;
    private final SessionRepository sessionRepository;
    private final GitSyncService gitSyncService;
    private final CheckpointGitReader gitReader;
    private final CheckpointMetadataParser metadataParser;
    private final SessionMetadataParser sessionMetadataParser;
    private final CheckpointSyncProperties syncProperties;

    public CheckpointSyncServiceImpl(
            RepoRepository repoRepository,
            CheckpointSyncStateRepository syncStateRepository,
            CheckpointRepository checkpointRepository,
            SessionRepository sessionRepository,
            GitSyncService gitSyncService,
            CheckpointGitReader gitReader,
            CheckpointMetadataParser metadataParser,
            SessionMetadataParser sessionMetadataParser,
            CheckpointSyncProperties syncProperties) {
        this.repoRepository = repoRepository;
        this.syncStateRepository = syncStateRepository;
        this.checkpointRepository = checkpointRepository;
        this.sessionRepository = sessionRepository;
        this.gitSyncService = gitSyncService;
        this.gitReader = gitReader;
        this.metadataParser = metadataParser;
        this.sessionMetadataParser = sessionMetadataParser;
        this.syncProperties = syncProperties;
    }

    @Override
    @Transactional
    public void syncRepo(Long repoId, boolean fullScan) {
        Repo repo = repoRepository.findById(repoId).orElse(null);
        if (repo == null) {
            log.warn("Checkpoint sync: repo not found, repoId={}", repoId);
            return;
        }
        // Entire CLI stores checkpoints either on a legacy metadata branch (entire/checkpoints/v1)
        // or, since CLI 0.13 by default, as one ref per checkpoint under refs/entire/checkpoints/.
        // Support both; a repo may even have both if it switched backends.
        String metadataBranch = syncProperties.getBranch();
        boolean hasMetadataBranch;
        boolean hasCheckpointRefs;
        try {
            hasMetadataBranch = remoteHasBranch(repoId, metadataBranch);
            hasCheckpointRefs = !gitSyncService.listRemoteRefs(repoId, CheckpointGitReader.CHECKPOINT_REFS_PREFIX).isEmpty();
        } catch (Exception e) {
            log.warn("Checkpoint sync: list remote refs failed for repoId={}, error={}", repoId, e.getMessage());
            return;
        }
        if (!hasMetadataBranch && !hasCheckpointRefs) {
            log.info("Checkpoint sync: repo {} has neither metadata branch {} nor {} refs, skip",
                    repoId, metadataBranch, CheckpointGitReader.CHECKPOINT_REFS_PREFIX);
            return;
        }

        // A shallow clone is replaced by a full one during git sync; walk everything again in that case
        if (gitReader.isShallow(repoId) && syncProperties.getCloneDepth() == 0) {
            fullScan = true;
        }
        try {
            gitSyncService.syncRepositoryAllBranchesForCheckpoint(repoId, metadataBranch, syncProperties.getCloneDepth());
        } catch (Exception e) {
            log.error("Checkpoint sync: Git sync failed, repoId={}, error={}", repoId, e.getMessage(), e);
            return;
        }

        // Fetch checkpoint metadata to latest before walking content branches
        String metadataRevision = null;
        if (hasMetadataBranch) {
            try {
                gitSyncService.fetchMetadataBranch(repoId, metadataBranch);
            } catch (Exception e) {
                log.error("Checkpoint sync: fetch metadata branch failed, repoId={}, branch={}, error={}", repoId, metadataBranch, e.getMessage(), e);
                return;
            }
            metadataRevision = gitReader.resolveBranchCommitSha(repoId, metadataBranch).orElse(null);
            if (metadataRevision == null) {
                log.warn("Checkpoint sync: metadata branch has no commit or not found, repoId={}, branch={}", repoId, metadataBranch);
            }
        }
        if (hasCheckpointRefs) {
            try {
                gitSyncService.fetchRefsByPrefix(repoId, CheckpointGitReader.CHECKPOINT_REFS_PREFIX);
            } catch (Exception e) {
                log.error("Checkpoint sync: fetch checkpoint refs failed, repoId={}, error={}", repoId, e.getMessage(), e);
                return;
            }
        }
        Map<String, String> checkpointRefs = gitReader.listCheckpointRefs(repoId);
        if (metadataRevision == null && checkpointRefs.isEmpty()) {
            log.warn("Checkpoint sync: no checkpoint metadata available locally, repoId={}", repoId);
            return;
        }

        List<String> contentBranches = gitReader.listLocalBranchNames(repoId).stream()
                .filter(b -> !metadataBranch.equals(b))
                .toList();
        if (contentBranches.isEmpty()) {
            log.info("Checkpoint sync: repo {} has no content branches (excluding {}), skip walk", repoId, metadataBranch);
            return;
        }

        // Incremental walk by branch: each branch uses its own lastProcessedCommitSha as stopAtSha
        List<CheckpointGitReader.CommitInfo> allCommits = new ArrayList<>();
        for (String branchName : contentBranches) {
            String stopAtSha = fullScan ? null : getLastProcessedCommitSha(repoId, branchName);
            log.debug("Checkpoint sync: processing branch {}, repoId={}", branchName, repoId);
            List<CheckpointGitReader.CommitInfo> commits = gitReader.walkCommitsFromBranch(repoId, branchName, stopAtSha);
            allCommits.addAll(commits);
            log.debug("Checkpoint sync: branch {} done, commits={}, repoId={}", branchName, commits.size(), repoId);
        }
        Collections.reverse(allCommits);

        for (CheckpointGitReader.CommitInfo commit : allCommits) {
            String checkpointId = parseTrailer(commit.fullMessage());
            if (checkpointId == null) {
                continue;
            }
            Optional<CheckpointGitReader.CheckpointLocation> locOpt =
                    CheckpointGitReader.locateCheckpoint(checkpointId, checkpointRefs, metadataRevision);
            if (locOpt.isEmpty()) {
                log.debug("Checkpoint storage not found: repoId={}, commit={}, checkpointId={}", repoId, commit.commitSha(), checkpointId);
                continue;
            }
            String checkpointRevision = locOpt.get().revision();
            String path = locOpt.get().basePath() + "metadata.json";
            Optional<String> metaOpt = gitReader.getFileContent(repoId, checkpointRevision, path);
            if (metaOpt.isEmpty()) {
                log.debug("Checkpoint metadata not found: repoId={}, commit={}, path={}", repoId, commit.commitSha(), path);
                continue;
            }
            String metaJson = metaOpt.get();
            CheckpointParseResult parsed = metadataParser.parseCheckpointMetadata(metaJson);
            if (parsed == null || parsed.getCheckpointId() == null) {
                continue;
            }
            String lastSessionPath = metadataParser.getLastSessionMetadataPath(metaJson);
            if (StrUtil.isNotBlank(lastSessionPath)) {
                Optional<String> sessionMeta = gitReader.getFileContent(repoId, checkpointRevision, lastSessionPath);
                parsed.setAgent(sessionMeta.map(metadataParser::parseSessionAgent).orElse(null));
            }
            CheckpointGitReader.LineStats lineStats = gitReader.getCommitLineStats(repoId, commit.commitSha()).orElse(null);
            Checkpoint savedCheckpoint = upsertCheckpoint(repoId, commit.commitSha(), commit.fullMessage(), commit.authorName(), commit.commitTime(), parsed, lineStats);
            upsertSessionsForCheckpoint(repoId, checkpointRevision, metaJson, savedCheckpoint.getId());
        }

        // Consistency: update state by branch only after all success (within transaction, any step failure rolls back entirely)
        long now = System.currentTimeMillis();
        for (String branchName : contentBranches) {
            String headSha = gitReader.resolveBranchCommitSha(repoId, branchName).orElse(null);
            if (headSha != null) {
                updateSyncStateSuccess(repoId, branchName, headSha, now);
            }
        }
        // Update repository table last successful sync time
        repo.setLastSuccessfulSyncAt(now);
        repoRepository.save(repo);
        log.info("Checkpoint sync done: repoId={}, contentBranches={}, processed={}", repoId, contentBranches.size(), allCommits.size());
    }

    @Override
    public void syncAllRepos(boolean fullScan) {
        List<Repo> repos = repoRepository.findAll();
        for (Repo repo : repos) {
            try {
                syncRepo(repo.getId(), fullScan);
            } catch (Exception e) {
                log.error("Checkpoint sync failed for repoId={}: {}", repo.getId(), e.getMessage(), e);
            }
        }
    }

    private boolean remoteHasBranch(Long repoId, String branch) {
        GitBranchesDTO dto = gitSyncService.getBranches(repoId);
        return dto.getBranches() != null && dto.getBranches().contains(branch);
    }

    private String parseTrailer(String fullMessage) {
        if (fullMessage == null) {
            return null;
        }
        Matcher m = TRAILER_PATTERN.matcher(fullMessage);
        String last = null;
        while (m.find()) {
            last = m.group(1);
        }
        return last;
    }

    private String getLastProcessedCommitSha(Long repoId, String branch) {
        return syncStateRepository.findByRepoIdAndBranch(repoId, branch)
                .map(CheckpointSyncState::getLastProcessedCommitSha)
                .orElse(null);
    }

    @Transactional
    protected Checkpoint upsertCheckpoint(Long repoId, String commitSha, String commitMessage, String commitAuthorName, long commitTime, CheckpointParseResult parsed, CheckpointGitReader.LineStats lineStats) {
        Checkpoint entity = checkpointRepository.findByRepoIdAndCheckpointId(repoId, parsed.getCheckpointId())
                .orElse(new Checkpoint());
        Repo repo = repoRepository.findById(repoId).orElse(null);
        entity.setCheckpointId(parsed.getCheckpointId());
        entity.setRepoId(repoId);
        entity.setRepoName(repo != null ? repo.getName() : null);
        entity.setBranch(parsed.getBranch());
        entity.setCommitSha(commitSha);
        entity.setCommitMessage(commitMessage);
        entity.setCommitAuthorName(commitAuthorName);
        entity.setCommitTime(commitTime);
        entity.setCheckpointsCount(parsed.getCheckpointsCount());
        entity.setFilesTouched(parsed.getFilesTouched());
        entity.setAdditions(lineStats != null ? lineStats.additions() : null);
        entity.setDeletions(lineStats != null ? lineStats.deletions() : null);
        entity.setTokenUsage(parsed.getTokenUsage());
        entity.setAgent(parsed.getAgent());
        return checkpointRepository.save(entity);
    }

    /// Upsert sessions for a checkpoint from metadata branch. Sessions array only (tasks/ excluded).
    private void upsertSessionsForCheckpoint(Long repoId, String metadataRevision, String checkpointMetaJson, Long checkpointId) {
        List<String> metadataPaths = metadataParser.getSessionMetadataPaths(checkpointMetaJson);
        for (int i = 0; i < metadataPaths.size(); i++) {
            String metaPath = metadataPaths.get(i);
            Optional<String> metaOpt = gitReader.getFileContent(repoId, metadataRevision, metaPath);
            if (metaOpt.isEmpty()) {
                log.debug("Session metadata not found: repoId={}, path={}", repoId, metaPath);
                continue;
            }
            SessionParseResult parsed = sessionMetadataParser.parse(metaOpt.get());
            if (parsed == null) {
                continue;
            }
            parsed.setSessionIndex(i);
            String promptPath = metadataParser.getSessionPromptPath(checkpointMetaJson, i);
            String promptPreview = null;
            if (StrUtil.isNotBlank(promptPath)) {
                Optional<String> promptOpt = gitReader.getFileContent(repoId, metadataRevision, promptPath);
                promptPreview = sessionMetadataParser.getFirstLineOfPrompt(promptOpt.orElse(null));
            }
            parsed.setPromptPreview(promptPreview);

            Session session = sessionRepository.findByCheckpointIdAndSessionIndex(checkpointId, i).orElse(new Session());
            session.setCheckpointId(checkpointId);
            session.setSessionIndex(i);
            copyParseResultToSession(parsed, session);
            sessionRepository.save(session);
        }
    }

    private void copyParseResultToSession(SessionParseResult from, Session to) {
        to.setSessionId(from.getSessionId());
        to.setStrategy(from.getStrategy());
        to.setSessionCreatedAt(from.getSessionCreatedAt());
        to.setBranch(from.getBranch());
        to.setCheckpointsCount(from.getCheckpointsCount());
        to.setFilesTouchedCount(from.getFilesTouchedCount());
        to.setFilesTouchedJson(from.getFilesTouchedJson());
        to.setAgent(from.getAgent());
        to.setInputTokens(from.getInputTokens());
        to.setOutputTokens(from.getOutputTokens());
        to.setApiCallCount(from.getApiCallCount());
        to.setAgentLines(from.getAgentLines());
        to.setHumanAdded(from.getHumanAdded());
        to.setHumanModified(from.getHumanModified());
        to.setHumanRemoved(from.getHumanRemoved());
        to.setTotalCommitted(from.getTotalCommitted());
        to.setAgentPercentage(from.getAgentPercentage());
        to.setPromptPreview(from.getPromptPreview());
    }

    private void updateSyncStateSuccess(Long repoId, String branch, String lastProcessedCommitSha, long now) {
        CheckpointSyncState state = syncStateRepository.findByRepoIdAndBranch(repoId, branch).orElseGet(() -> {
            CheckpointSyncState s = new CheckpointSyncState();
            s.setRepoId(repoId);
            s.setBranch(branch);
            s.setCreatedAt(now);
            return s;
        });
        state.setLastProcessedCommitSha(lastProcessedCommitSha);
        state.setLastSyncAt(now);
        state.setLastError(null);
        state.setUpdatedAt(now);
        syncStateRepository.save(state);
    }
}
