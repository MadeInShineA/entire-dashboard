package com.mzfuture.entire.checkpoint.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mzfuture.entire.checkpoint.config.CheckpointSyncProperties;
import com.mzfuture.entire.checkpoint.dto.response.CheckpointDTO;
import com.mzfuture.entire.checkpoint.dto.response.FileHistoryDTO;
import com.mzfuture.entire.checkpoint.dto.response.FileListDTO;
import com.mzfuture.entire.checkpoint.entity.Checkpoint;
import com.mzfuture.entire.checkpoint.entity.Session;
import com.mzfuture.entire.checkpoint.git.CheckpointGitReader;
import com.mzfuture.entire.checkpoint.mapper.SessionMapper;
import com.mzfuture.entire.checkpoint.repository.CheckpointRepository;
import com.mzfuture.entire.checkpoint.repository.SessionRepository;
import com.mzfuture.entire.checkpoint.service.CheckpointService;
import com.mzfuture.entire.checkpoint.service.FileHistoryService;
import com.mzfuture.entire.common.exception.Errors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/// File-centric view: which AI sessions touched a file, and which checkpoint/session each current line comes from.
@Slf4j
@Service
public class FileHistoryServiceImpl implements FileHistoryService {

    /// Lines beyond this are not attributed (keeps responses small for generated / huge files)
    private static final int MAX_BLAME_LINES = 5000;

    private final CheckpointGitReader gitReader;
    private final CheckpointRepository checkpointRepository;
    private final SessionRepository sessionRepository;
    private final SessionMapper sessionMapper;
    private final CheckpointService checkpointService;
    private final CheckpointSyncProperties syncProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FileHistoryServiceImpl(CheckpointGitReader gitReader,
                                  CheckpointRepository checkpointRepository,
                                  SessionRepository sessionRepository,
                                  SessionMapper sessionMapper,
                                  CheckpointService checkpointService,
                                  CheckpointSyncProperties syncProperties) {
        this.gitReader = gitReader;
        this.checkpointRepository = checkpointRepository;
        this.sessionRepository = sessionRepository;
        this.sessionMapper = sessionMapper;
        this.checkpointService = checkpointService;
        this.syncProperties = syncProperties;
    }

    @Override
    public FileListDTO listFiles(Long repoId, String branch) {
        List<String> branches = contentBranches(repoId);
        String effectiveBranch = resolveBranch(branches, branch);

        Map<String, Integer> sessionCounts = new HashMap<>();
        for (Session s : sessionRepository.findAllByRepoId(repoId)) {
            for (String path : filesTouched(s)) {
                sessionCounts.merge(path, 1, Integer::sum);
            }
        }

        Map<String, FileListDTO.FileEntryDTO> entries = new TreeMap<>();
        CheckpointGitReader.BranchFiles head = effectiveBranch != null
                ? gitReader.listFiles(repoId, effectiveBranch)
                : new CheckpointGitReader.BranchFiles(List.of(), Set.of());
        for (String path : head.files()) {
            entries.put(path, entry(path, sessionCounts.getOrDefault(path, 0), true));
        }
        // Files touched by sessions but deleted/renamed since: still useful to see which sessions touched them.
        // Submodule paths are not files (sessions list them when a submodule pointer changes).
        sessionCounts.forEach((path, count) -> {
            if (!head.submodules().contains(path)) {
                entries.computeIfAbsent(path, p -> entry(p, count, false));
            }
        });

        FileListDTO dto = new FileListDTO();
        dto.setBranches(branches);
        dto.setBranch(effectiveBranch);
        dto.setFiles(new ArrayList<>(entries.values()));
        return dto;
    }

    @Override
    public FileHistoryDTO getFileHistory(Long repoId, String branch, String path) {
        if (path == null || path.isBlank()) {
            throw Errors.BAD_REQUEST.toException("path is required");
        }
        String effectiveBranch = resolveBranch(contentBranches(repoId), branch);

        FileHistoryDTO dto = new FileHistoryDTO();
        dto.setPath(path);
        dto.setBranch(effectiveBranch);
        dto.setLines(new ArrayList<>());

        // 1) Sessions whose files_touched lists the file
        List<Session> repoSessions = sessionRepository.findAllByRepoId(repoId);
        Map<Long, List<Session>> sessionsByCheckpoint = repoSessions.stream()
                .sorted(Comparator.comparing(Session::getSessionIndex, Comparator.nullsFirst(Integer::compareTo)))
                .collect(Collectors.groupingBy(Session::getCheckpointId, LinkedHashMap::new, Collectors.toList()));
        Set<Long> touchingSessionIds = new HashSet<>();
        for (Session s : repoSessions) {
            if (filesTouched(s).contains(path)) {
                touchingSessionIds.add(s.getId());
            }
        }

        // 2) Line-by-line attribution of the current version
        CheckpointGitReader.BlameData blame = effectiveBranch == null ? null
                : gitReader.blame(repoId, effectiveBranch, path, MAX_BLAME_LINES).orElse(null);
        Map<String, Checkpoint> checkpointByCommit = new HashMap<>();
        if (blame != null) {
            dto.setExistsAtHead(true);
            dto.setHeadSha(blame.headSha());
            dto.setBinary(blame.binary());
            dto.setTruncated(blame.truncated());
            Set<String> shas = blame.lines().stream().map(CheckpointGitReader.BlameLine::commitSha)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            if (!shas.isEmpty()) {
                for (Checkpoint c : checkpointRepository.findByRepoIdAndCommitShaIn(repoId, shas)) {
                    checkpointByCommit.put(c.getCommitSha(), c);
                }
            }
        }

        // For each blamed checkpoint, the session the lines are attributed to:
        // the last session that lists the file, else the last session of the checkpoint.
        Map<Long, Session> attributedSession = new HashMap<>();
        for (Checkpoint c : checkpointByCommit.values()) {
            List<Session> sessions = sessionsByCheckpoint.getOrDefault(c.getId(), List.of());
            Session pick = null;
            for (Session s : sessions) {
                if (touchingSessionIds.contains(s.getId())) {
                    pick = s;
                }
            }
            if (pick == null && !sessions.isEmpty()) {
                pick = sessions.get(sessions.size() - 1);
            }
            if (pick != null) {
                attributedSession.put(c.getId(), pick);
            }
        }

        // Key: session id, or -checkpointId when a checkpoint has no session
        Map<Long, Integer> linesPerKey = new HashMap<>();
        int checkpointLines = 0;
        if (blame != null) {
            int n = 1;
            for (CheckpointGitReader.BlameLine bl : blame.lines()) {
                FileHistoryDTO.FileLineDTO line = new FileHistoryDTO.FileLineDTO();
                line.setNumber(n++);
                line.setContent(bl.content());
                line.setCommitSha(bl.commitSha());
                line.setAuthorName(bl.authorName());
                line.setCommitTime(bl.commitTime());
                Checkpoint c = bl.commitSha() != null ? checkpointByCommit.get(bl.commitSha()) : null;
                if (c != null) {
                    checkpointLines++;
                    line.setCheckpointId(c.getId());
                    Session s = attributedSession.get(c.getId());
                    line.setSessionId(s != null ? s.getId() : null);
                    linesPerKey.merge(s != null ? s.getId() : -c.getId(), 1, Integer::sum);
                }
                dto.getLines().add(line);
            }
            dto.setTotalLines(blame.lines().size());
        }
        dto.setCheckpointLines(checkpointLines);

        // 3) Session list = sessions listing the file ∪ sessions/checkpoints that own current lines
        Set<Long> checkpointIds = new HashSet<>(checkpointByCommit.values().stream().map(Checkpoint::getId).toList());
        for (Session s : repoSessions) {
            if (touchingSessionIds.contains(s.getId())) {
                checkpointIds.add(s.getCheckpointId());
            }
        }
        Map<Long, CheckpointDTO> checkpointDtos = checkpointService
                .toDTOs(checkpointRepository.findAllById(checkpointIds)).stream()
                .collect(Collectors.toMap(CheckpointDTO::getId, c -> c));

        List<FileHistoryDTO.FileSessionDTO> rows = new ArrayList<>();
        for (Long checkpointId : checkpointIds) {
            CheckpointDTO cdto = checkpointDtos.get(checkpointId);
            if (cdto == null) {
                continue;
            }
            List<Session> sessions = sessionsByCheckpoint.getOrDefault(checkpointId, List.of());
            Session attributed = attributedSession.get(checkpointId);
            boolean anyRow = false;
            for (Session s : sessions) {
                boolean touched = touchingSessionIds.contains(s.getId());
                if (!touched && s != attributed) {
                    continue;
                }
                rows.add(row(cdto, s, touched, linesPerKey.getOrDefault(s.getId(), 0)));
                anyRow = true;
            }
            if (!anyRow) {
                rows.add(row(cdto, null, false, linesPerKey.getOrDefault(-checkpointId, 0)));
            }
        }
        rows.sort(Comparator.comparing((FileHistoryDTO.FileSessionDTO r) -> r.getCheckpoint().getCommitTime()).reversed()
                .thenComparing(r -> r.getSession() != null ? r.getSession().getSessionIndex() : 0));
        dto.setSessions(rows);
        return dto;
    }

    private FileHistoryDTO.FileSessionDTO row(CheckpointDTO checkpoint, Session session, boolean touched, int currentLines) {
        FileHistoryDTO.FileSessionDTO r = new FileHistoryDTO.FileSessionDTO();
        r.setCheckpoint(checkpoint);
        r.setSession(session != null ? sessionMapper.toDTO(session) : null);
        r.setTouchedFile(touched);
        r.setCurrentLines(currentLines);
        return r;
    }

    private static FileListDTO.FileEntryDTO entry(String path, int sessionCount, boolean existsAtHead) {
        FileListDTO.FileEntryDTO e = new FileListDTO.FileEntryDTO();
        e.setPath(path);
        e.setSessionCount(sessionCount);
        e.setExistsAtHead(existsAtHead);
        return e;
    }

    private List<String> contentBranches(Long repoId) {
        String metadataBranch = syncProperties.getBranch();
        return gitReader.listLocalBranchNames(repoId).stream()
                .filter(b -> !b.equals(metadataBranch))
                .toList();
    }

    /// Requested branch if it exists locally, else main, else master, else the first one
    private static String resolveBranch(List<String> branches, String requested) {
        if (requested != null && branches.contains(requested)) {
            return requested;
        }
        for (String preferred : List.of("main", "master")) {
            if (branches.contains(preferred)) {
                return preferred;
            }
        }
        return branches.isEmpty() ? null : branches.get(0);
    }

    private List<String> filesTouched(Session s) {
        String json = s.getFilesTouchedJson();
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.debug("Bad files_touched_json for session {}: {}", s.getId(), e.getMessage());
            return Collections.emptyList();
        }
    }
}
