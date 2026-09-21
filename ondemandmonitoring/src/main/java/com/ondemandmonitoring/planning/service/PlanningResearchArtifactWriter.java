package com.ondemandmonitoring.planning.service;

import com.ondemandmonitoring.planning.dto.PlanningResearchArtifactBundle;
import com.ondemandmonitoring.planning.dto.PlanningResearchArtifactWriteResult;
import java.io.IOException;
import java.nio.file.Path;

public interface PlanningResearchArtifactWriter {

    PlanningResearchArtifactWriteResult write(
            PlanningResearchArtifactBundle bundle,
            Path outputDirectory) throws IOException;
}
