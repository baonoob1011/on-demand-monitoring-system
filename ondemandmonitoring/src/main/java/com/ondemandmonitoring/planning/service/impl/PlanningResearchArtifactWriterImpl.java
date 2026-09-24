package com.ondemandmonitoring.planning.service.impl;

import com.ondemandmonitoring.planning.dto.PlanningResearchArtifactBundle;
import com.ondemandmonitoring.planning.dto.PlanningResearchArtifactWriteResult;
import com.ondemandmonitoring.planning.service.PlanningResearchArtifactWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PlanningResearchArtifactWriterImpl implements PlanningResearchArtifactWriter {

    @Override
    public PlanningResearchArtifactWriteResult write(
            PlanningResearchArtifactBundle bundle,
            Path outputDirectory) throws IOException {
        if (bundle == null) throw new IllegalArgumentException("Artifact bundle is required.");
        if (outputDirectory == null) throw new IllegalArgumentException("Output directory is required.");
        Path safeOutputDirectory = outputDirectory.toAbsolutePath().normalize();
        Files.createDirectories(safeOutputDirectory);
        Map<String, String> files = files(bundle);
        List<Path> written = new ArrayList<>();
        for (Map.Entry<String, String> entry : files.entrySet()) {
            Path file = safeOutputDirectory.resolve(entry.getKey()).normalize();
            if (!file.getParent().equals(safeOutputDirectory)) {
                throw new IOException("Resolved artifact path escaped output directory: " + file);
            }
            Files.writeString(file, entry.getValue(), StandardCharsets.UTF_8);
            written.add(file);
        }
        return new PlanningResearchArtifactWriteResult(safeOutputDirectory, written);
    }

    private Map<String, String> files(PlanningResearchArtifactBundle bundle) {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("methodology-summary.md", bundle.methodologySummaryMarkdown());
        files.put("algorithm-summary.csv", bundle.algorithmSummaryCsv());
        files.put("paired-scenarios.csv", bundle.pairedScenarioCsv());
        files.put("statistical-summary.csv", bundle.statisticalSummaryCsv());
        files.put("figure-energy-saving.csv", bundle.figureEnergySavingCsv());
        files.put("figure-distance-energy-tradeoff.csv", bundle.figureDistanceEnergyTradeoffCsv());
        files.put("figure-world-z-difference.csv", bundle.figureWorldZDifferenceCsv());
        files.put("figure-planning-time.csv", bundle.figurePlanningTimeCsv());
        files.put("figure-energy-distribution.csv", bundle.figureEnergyDistributionCsv());
        files.put("figure-planning-time-distribution.csv", bundle.figurePlanningTimeDistributionCsv());
        files.put("research-artifact-manifest.json", bundle.manifestJson());
        return files;
    }
}
