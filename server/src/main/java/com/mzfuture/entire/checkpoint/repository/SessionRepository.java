package com.mzfuture.entire.checkpoint.repository;

import com.mzfuture.entire.checkpoint.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SessionRepository extends JpaRepository<Session, Long> {

    /// Find all sessions for a checkpoint, ordered by session_index
    List<Session> findByCheckpointIdOrderBySessionIndexAsc(Long checkpointId);

    /// Find by checkpoint and session index (for upsert)
    Optional<Session> findByCheckpointIdAndSessionIndex(Long checkpointId, Integer sessionIndex);

    /// All sessions of all checkpoints of a repository
    @org.springframework.data.jpa.repository.Query(
            "SELECT s FROM Session s WHERE s.checkpointId IN (SELECT c.id FROM Checkpoint c WHERE c.repoId = :repoId)")
    List<Session> findAllByRepoId(@org.springframework.data.repository.query.Param("repoId") Long repoId);

    /// Per-checkpoint session sums for checkpoints committed in a time range (Entire CLI 0.13.2+ metadata).
    /// Columns: checkpointId, sessionCount, inputTokens, outputTokens, cacheCreationTokens, cacheReadTokens,
    /// apiCallCount, turnCount, model (one of the sessions' models; sessions of a checkpoint normally share one)
    @org.springframework.data.jpa.repository.Query("""
            SELECT s.checkpointId, COUNT(s.id),
                   COALESCE(SUM(s.inputTokens), 0), COALESCE(SUM(s.outputTokens), 0),
                   COALESCE(SUM(s.cacheCreationTokens), 0), COALESCE(SUM(s.cacheReadTokens), 0),
                   COALESCE(SUM(s.apiCallCount), 0), COALESCE(SUM(s.turnCount), 0),
                   MAX(s.model)
            FROM Session s
            WHERE s.checkpointId IN (SELECT c.id FROM Checkpoint c WHERE c.commitTime >= :start AND c.commitTime <= :end)
            GROUP BY s.checkpointId
            """)
    List<Object[]> sumByCheckpointCommittedBetween(
            @org.springframework.data.repository.query.Param("start") Long start,
            @org.springframework.data.repository.query.Param("end") Long end);
}
