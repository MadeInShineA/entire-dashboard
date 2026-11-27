package com.mzfuture.entire.overview.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/// Overview analytics response: every checkpoint in range, newest first
@Data
@Schema(description = "Overview analytics response")
public class OverviewAnalyticsDTO {

    @Schema(description = "Checkpoints in range with session aggregates, newest first")
    private List<OverviewAnalyticsRowDTO> rows;

    @Schema(description = "Whether rows were capped (oldest dropped)")
    private Boolean truncated;

    @Schema(description = "Row cap")
    private Integer limit;
}
