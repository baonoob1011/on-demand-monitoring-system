package com.ondemandmonitoring.planning.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ondemandmonitoring.planning.dto.PlanningResearchArtifactBundle;
import com.ondemandmonitoring.planning.dto.PlanningResearchArtifactWriteResult;
import com.ondemandmonitoring.planning.service.impl.PlanningResearchArtifactWriterImpl;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlanningResearchArtifactWriterTest {

    private final PlanningResearchArtifactWriter writer = new PlanningResearchArtifactWriterImpl();

    @Test
    void writesKnownUtf8FilesAndReplacesExistingContent(@TempDir Path tempDir) throws Exception {
        PlanningResearchArtifactBundle bundle = bundle("first");
        Path out = tempDir.resolve("nested");

        PlanningResearchArtifactWriteResult result = writer.write(bundle, out);
        Files.writeString(out.resolve("algorithm-summary.csv"), "old");
        PlanningResearchArtifactWriteResult second = writer.write(bundle("second"), out);

        assertThat(result.writtenFiles()).hasSize(11);
        assertThat(second.writtenFiles()).hasSize(11);
        assertThat(Files.readString(out.resolve("methodology-summary.md"))).isEqualTo("second");
        assertThat(Files.readString(out.resolve("algorithm-summary.csv"))).isEqualTo("second");
        assertThat(second.writtenFiles()).extracting(path -> path.getFileName().toString()).containsExactly(
                "methodology-summary.md", "algorithm-summary.csv", "paired-scenarios.csv",
                "statistical-summary.csv", "figure-energy-saving.csv", "figure-distance-energy-tradeoff.csv",
                "figure-world-z-difference.csv", "figure-planning-time.csv", "figure-energy-distribution.csv",
                "figure-planning-time-distribution.csv", "research-artifact-manifest.json");
    }

    private PlanningResearchArtifactBundle bundle(String content) {
        return new PlanningResearchArtifactBundle("EXP", content, content, content, content, content,
                content, content, content, content, content, content);
    }
}
