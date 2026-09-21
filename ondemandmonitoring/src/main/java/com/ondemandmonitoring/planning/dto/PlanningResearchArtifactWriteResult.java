package com.ondemandmonitoring.planning.dto;

import java.nio.file.Path;
import java.util.List;

public record PlanningResearchArtifactWriteResult(Path outputDirectory, List<Path> writtenFiles) {

    public PlanningResearchArtifactWriteResult {
        writtenFiles = List.copyOf(writtenFiles);
    }
}
