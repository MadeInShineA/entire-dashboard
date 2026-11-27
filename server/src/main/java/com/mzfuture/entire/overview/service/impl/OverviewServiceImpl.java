package com.mzfuture.entire.overview.service.impl;

import com.mzfuture.entire.checkpoint.entity.Checkpoint;
import com.mzfuture.entire.checkpoint.entity.QCheckpoint;
import com.mzfuture.entire.checkpoint.repository.CheckpointRepository;
import com.mzfuture.entire.checkpoint.repository.SessionRepository;
import com.mzfuture.entire.overview.dto.request.OverviewAnalyticsParams;
import com.mzfuture.entire.overview.dto.response.OverviewAnalyticsDTO;
import com.mzfuture.entire.overview.dto.response.OverviewAnalyticsRowDTO;
import com.mzfuture.entire.common.dto.PagerPayload;
import com.mzfuture.entire.common.exception.Errors;
import com.mzfuture.entire.gitrepo.entity.Repo;
import com.mzfuture.entire.gitrepo.repository.RepoRepository;
import com.mzfuture.entire.overview.dto.request.OverviewCheckpointsParams;
import com.mzfuture.entire.overview.dto.request.OverviewStatsParams;
import com.mzfuture.entire.overview.dto.response.OverviewAgentStatDTO;
import com.mzfuture.entire.overview.dto.response.OverviewCheckpointChartItemDTO;
import com.mzfuture.entire.overview.dto.response.OverviewCheckpointListItemDTO;
import com.mzfuture.entire.overview.dto.response.OverviewCheckpointsResponseDTO;
import com.mzfuture.entire.overview.dto.response.OverviewStatsDTO;
import com.mzfuture.entire.overview.service.OverviewService;
import com.querydsl.core.BooleanBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/// Overview statistics service implementation
@Service
@RequiredArgsConstructor
public class OverviewServiceImpl implements OverviewService {

    private static final long MAX_RANGE_MS = 90L * 24 * 60 * 60 * 1000;
    private static final int CHART_DATA_LIMIT = 500;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private static final int ANALYTICS_ROW_LIMIT = 20_000;

    private final CheckpointRepository checkpointRepository;
    private final SessionRepository sessionRepository;
    private final RepoRepository repoRepository;

    @Override
    public OverviewStatsDTO getStats(OverviewStatsParams params) {
        ZoneId zone = ZoneId.systemDefault();
        long start = params.getStartTime() != null
                ? params.getStartTime()
                : LocalDate.now(zone).minusDays(7).atStartOfDay(zone).toInstant().toEpochMilli();
        long end = params.getEndTime() != null
                ? params.getEndTime()
                : LocalDate.now(zone).atTime(LocalTime.of(23, 59, 59, 999_000_000)).atZone(zone).toInstant().toEpochMilli();

        if (start >= end) {
            throw Errors.INVALID_ARGUMENT.toException("startTime must be less than endTime");
        }
        if (end - start > MAX_RANGE_MS) {
            throw Errors.INVALID_ARGUMENT.toException("Time range must not exceed 90 days");
        }

        Long activeProjectCount = checkpointRepository.countDistinctRepoIdsByCommitTimeBetween(start, end);
        Long submitterCount = checkpointRepository.countDistinctCommitAuthorsByCommitTimeBetween(start, end);
        Long checkpointCount = checkpointRepository.countByCommitTimeBetween(start, end);
        Long totalTokenUsage = checkpointRepository.sumTokenUsageByCommitTimeBetween(start, end);

        OverviewStatsDTO dto = new OverviewStatsDTO();
        dto.setActiveProjectCount(activeProjectCount != null ? activeProjectCount : 0L);
        dto.setSubmitterCount(submitterCount != null ? submitterCount : 0L);
        dto.setCheckpointCount(checkpointCount != null ? checkpointCount : 0L);
        dto.setTotalTokenUsage(totalTokenUsage != null ? totalTokenUsage : 0L);
        return dto;
    }

    @Override
    public OverviewCheckpointsResponseDTO getCheckpoints(OverviewCheckpointsParams params) {
        ZoneId zone = ZoneId.systemDefault();
        long start = params.getStartTime() != null
                ? params.getStartTime()
                : LocalDate.now(zone).minusDays(7).atStartOfDay(zone).toInstant().toEpochMilli();
        long end = params.getEndTime() != null
                ? params.getEndTime()
                : LocalDate.now(zone).atTime(LocalTime.of(23, 59, 59, 999_000_000)).atZone(zone).toInstant().toEpochMilli();

        if (start >= end) {
            throw Errors.INVALID_ARGUMENT.toException("startTime must be less than endTime");
        }
        if (end - start > MAX_RANGE_MS) {
            throw Errors.INVALID_ARGUMENT.toException("Time range must not exceed 90 days");
        }

        QCheckpoint q = QCheckpoint.checkpoint;
        BooleanBuilder timeRange = new BooleanBuilder()
                .and(q.commitTime.goe(start))
                .and(q.commitTime.loe(end));

        // Chart data: most recent 500 in range (by commitTime)
        Pageable chartPageable = PageRequest.of(0, CHART_DATA_LIMIT, Sort.by(Sort.Direction.DESC, "commitTime"));
        var chartPage = checkpointRepository.findAll(timeRange, chartPageable);
        List<Checkpoint> chartCheckpoints = chartPage.getContent();
        long totalInRange = chartPage.getTotalElements();
        boolean chartDataTruncated = totalInRange > CHART_DATA_LIMIT;

        List<OverviewCheckpointChartItemDTO> chartData = chartCheckpoints.stream()
                .map(this::toChartItem)
                .collect(Collectors.toList());

        // Agent stats from chart data
        Map<String, Long> agentCounts = chartData.stream()
                .collect(Collectors.groupingBy(
                        d -> d.getAgent() != null ? d.getAgent() : "Unknown",
                        Collectors.counting()));
        int chartTotal = chartData.size();
        List<OverviewAgentStatDTO> agentStats = agentCounts.entrySet().stream()
                .map(e -> new OverviewAgentStatDTO(
                        e.getKey(),
                        e.getValue(),
                        chartTotal > 0 ? (int) Math.round(100.0 * e.getValue() / chartTotal) : 0))
                .collect(Collectors.toList());

        // List: paginated
        int page = params.getPage() != null ? Math.max(0, params.getPage()) : 0;
        int size = params.getSize() != null
                ? Math.min(MAX_PAGE_SIZE, Math.max(1, params.getSize()))
                : DEFAULT_PAGE_SIZE;
        Pageable listPageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "commitTime"));
        var listPage = checkpointRepository.findAll(timeRange, listPageable);
        List<Checkpoint> listCheckpoints = listPage.getContent();

        // Batch fetch repo names
        List<Long> repoIds = listCheckpoints.stream()
                .map(Checkpoint::getRepoId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> repoNameMap = repoRepository.findAllById(repoIds).stream()
                .collect(Collectors.toMap(Repo::getId, Repo::getName, (a, b) -> a));

        List<OverviewCheckpointListItemDTO> listItems = listCheckpoints.stream()
                .map(c -> toListItem(c, repoNameMap.getOrDefault(c.getRepoId(), "")))
                .collect(Collectors.toList());

        PagerPayload<OverviewCheckpointListItemDTO> list = new PagerPayload<>(
                listItems, listPage.getTotalElements(), listPageable);

        OverviewCheckpointsResponseDTO response = new OverviewCheckpointsResponseDTO();
        response.setChartData(chartData);
        response.setChartDataTruncated(chartDataTruncated);
        response.setList(list);
        response.setAgentStats(agentStats);
        return response;
    }

    @Override
    public PagerPayload<OverviewCheckpointListItemDTO> getCheckpointsList(OverviewCheckpointsParams params) {
        ZoneId zone = ZoneId.systemDefault();
        long start = params.getStartTime() != null
                ? params.getStartTime()
                : LocalDate.now(zone).minusDays(7).atStartOfDay(zone).toInstant().toEpochMilli();
        long end = params.getEndTime() != null
                ? params.getEndTime()
                : LocalDate.now(zone).atTime(LocalTime.of(23, 59, 59, 999_000_000)).atZone(zone).toInstant().toEpochMilli();

        if (start >= end) {
            throw Errors.INVALID_ARGUMENT.toException("startTime must be less than endTime");
        }
        if (end - start > MAX_RANGE_MS) {
            throw Errors.INVALID_ARGUMENT.toException("Time range must not exceed 90 days");
        }

        QCheckpoint q = QCheckpoint.checkpoint;
        BooleanBuilder timeRange = new BooleanBuilder()
                .and(q.commitTime.goe(start))
                .and(q.commitTime.loe(end));

        int page = params.getPage() != null ? Math.max(0, params.getPage()) : 0;
        int size = params.getSize() != null
                ? Math.min(MAX_PAGE_SIZE, Math.max(1, params.getSize()))
                : DEFAULT_PAGE_SIZE;
        Pageable listPageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "commitTime"));
        var listPage = checkpointRepository.findAll(timeRange, listPageable);
        List<Checkpoint> listCheckpoints = listPage.getContent();

        List<Long> repoIds = listCheckpoints.stream()
                .map(Checkpoint::getRepoId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> repoNameMap = repoRepository.findAllById(repoIds).stream()
                .collect(Collectors.toMap(Repo::getId, Repo::getName, (a, b) -> a));

        List<OverviewCheckpointListItemDTO> listItems = listCheckpoints.stream()
                .map(c -> toListItem(c, repoNameMap.getOrDefault(c.getRepoId(), "")))
                .collect(Collectors.toList());

        return new PagerPayload<>(listItems, listPage.getTotalElements(), listPageable);
    }

    @Override
    public OverviewAnalyticsDTO getAnalytics(OverviewAnalyticsParams params) {
        ZoneId zone = ZoneId.systemDefault();
        long start = params.getStartTime() != null
                ? params.getStartTime()
                : LocalDate.now(zone).minusDays(14).atStartOfDay(zone).toInstant().toEpochMilli();
        long end = params.getEndTime() != null
                ? params.getEndTime()
                : LocalDate.now(zone).atTime(LocalTime.of(23, 59, 59, 999_000_000)).atZone(zone).toInstant().toEpochMilli();

        if (start >= end) {
            throw Errors.INVALID_ARGUMENT.toException("startTime must be less than endTime");
        }
        // Twice the dashboard's 90-day range, so the client can fetch the previous period in the same call
        if (end - start > 2 * MAX_RANGE_MS) {
            throw Errors.INVALID_ARGUMENT.toException("Time range must not exceed 180 days");
        }

        QCheckpoint q = QCheckpoint.checkpoint;
        BooleanBuilder timeRange = new BooleanBuilder()
                .and(q.commitTime.goe(start))
                .and(q.commitTime.loe(end));
        var page = checkpointRepository.findAll(timeRange,
                PageRequest.of(0, ANALYTICS_ROW_LIMIT, Sort.by(Sort.Direction.DESC, "commitTime")));
        List<Checkpoint> checkpoints = page.getContent();

        Map<Long, Object[]> sumsByCheckpoint = sessionRepository.sumByCheckpointCommittedBetween(start, end).stream()
                .collect(Collectors.toMap(r -> ((Number) r[0]).longValue(), r -> r, (a, b) -> a));

        List<Long> repoIds = checkpoints.stream()
                .map(Checkpoint::getRepoId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> repoNameMap = repoRepository.findAllById(repoIds).stream()
                .collect(Collectors.toMap(Repo::getId, Repo::getName, (a, b) -> a));

        List<OverviewAnalyticsRowDTO> rows = new ArrayList<>(checkpoints.size());
        for (Checkpoint c : checkpoints) {
            rows.add(toAnalyticsRow(c, repoNameMap.getOrDefault(c.getRepoId(), ""), sumsByCheckpoint.get(c.getId())));
        }

        OverviewAnalyticsDTO dto = new OverviewAnalyticsDTO();
        dto.setRows(rows);
        dto.setTruncated(page.getTotalElements() > ANALYTICS_ROW_LIMIT);
        dto.setLimit(ANALYTICS_ROW_LIMIT);
        return dto;
    }

    private OverviewAnalyticsRowDTO toAnalyticsRow(Checkpoint c, String repoName, Object[] sums) {
        OverviewAnalyticsRowDTO dto = new OverviewAnalyticsRowDTO();
        dto.setId(c.getId());
        dto.setCheckpointId(c.getCheckpointId());
        dto.setRepoId(c.getRepoId());
        dto.setRepoName(repoName);
        dto.setBranch(c.getBranch());
        dto.setCommitSha(c.getCommitSha());
        dto.setCommitMessage(firstLine(c.getCommitMessage()));
        dto.setCommitAuthorName(c.getCommitAuthorName());
        dto.setCommitTime(c.getCommitTime());
        dto.setAgent(c.getAgent());
        dto.setFilesTouched(c.getFilesTouched() != null ? c.getFilesTouched() : 0);
        dto.setAdditions(c.getAdditions() != null ? c.getAdditions() : 0);
        dto.setDeletions(c.getDeletions() != null ? c.getDeletions() : 0);
        dto.setTokenUsage(c.getTokenUsage() != null ? c.getTokenUsage() : 0L);
        if (sums == null) {
            dto.setSessionCount(0);
            dto.setInputTokens(0L);
            dto.setOutputTokens(0L);
            dto.setCacheCreationTokens(0L);
            dto.setCacheReadTokens(0L);
            dto.setApiCallCount(0L);
            dto.setTurnCount(0L);
            return dto;
        }
        dto.setSessionCount(((Number) sums[1]).intValue());
        dto.setInputTokens(((Number) sums[2]).longValue());
        dto.setOutputTokens(((Number) sums[3]).longValue());
        dto.setCacheCreationTokens(((Number) sums[4]).longValue());
        dto.setCacheReadTokens(((Number) sums[5]).longValue());
        dto.setApiCallCount(((Number) sums[6]).longValue());
        dto.setTurnCount(((Number) sums[7]).longValue());
        dto.setModel((String) sums[8]);
        return dto;
    }

    private static String firstLine(String message) {
        if (message == null) return null;
        int nl = message.indexOf('\n');
        String line = (nl >= 0 ? message.substring(0, nl) : message).strip();
        return line.length() > 200 ? line.substring(0, 200) : line;
    }

    private OverviewCheckpointChartItemDTO toChartItem(Checkpoint c) {
        OverviewCheckpointChartItemDTO dto = new OverviewCheckpointChartItemDTO();
        dto.setCheckpointId(c.getCheckpointId());
        dto.setCommitTime(c.getCommitTime());
        dto.setAdditions(c.getAdditions() != null ? c.getAdditions() : 0);
        dto.setDeletions(c.getDeletions() != null ? c.getDeletions() : 0);
        dto.setAgent(c.getAgent());
        return dto;
    }

    private OverviewCheckpointListItemDTO toListItem(Checkpoint c, String repoName) {
        OverviewCheckpointListItemDTO dto = new OverviewCheckpointListItemDTO();
        dto.setId(c.getId());
        dto.setCheckpointId(c.getCheckpointId());
        dto.setCommitMessage(c.getCommitMessage());
        dto.setCommitAuthorName(c.getCommitAuthorName());
        dto.setRepoName(repoName);
        dto.setBranch(c.getBranch());
        dto.setAgent(c.getAgent());
        dto.setFilesTouched(c.getFilesTouched() != null ? c.getFilesTouched() : 0);
        dto.setAdditions(c.getAdditions() != null ? c.getAdditions() : 0);
        dto.setDeletions(c.getDeletions() != null ? c.getDeletions() : 0);
        dto.setCommitTime(c.getCommitTime());
        dto.setTokenUsage(c.getTokenUsage() != null ? c.getTokenUsage() : 0L);
        return dto;
    }
}
