package com.mzfuture.entire.overview.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/// Overview analytics query parameters
@Data
@Schema(description = "Overview analytics query parameters")
public class OverviewAnalyticsParams {

    @Schema(description = "Start time (Unix ms), inclusive. Usually the start of the previous period, so the client can compare periods.", example = "1709827200000")
    private Long startTime;

    @Schema(description = "End time (Unix ms), inclusive", example = "1710431999999")
    private Long endTime;
}
