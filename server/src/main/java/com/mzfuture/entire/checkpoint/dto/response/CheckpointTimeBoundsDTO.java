package com.mzfuture.entire.checkpoint.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/// Commit time of the oldest and newest checkpoint, null when there are none
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Commit time bounds of all checkpoints")
public class CheckpointTimeBoundsDTO {

    @Schema(description = "Commit time of the oldest checkpoint (Unix ms)", example = "1709827200000")
    private Long firstCommitTime;

    @Schema(description = "Commit time of the newest checkpoint (Unix ms)", example = "1710431999999")
    private Long lastCommitTime;
}
