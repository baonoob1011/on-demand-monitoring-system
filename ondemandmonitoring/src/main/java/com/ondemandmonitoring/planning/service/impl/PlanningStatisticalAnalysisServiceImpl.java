package com.ondemandmonitoring.planning.service.impl;

import com.ondemandmonitoring.planning.dto.ConfidenceInterval;
import com.ondemandmonitoring.planning.dto.EffectSizeResult;
import com.ondemandmonitoring.planning.dto.MetricDistributionSummary;
import com.ondemandmonitoring.planning.dto.MetricStatisticalAnalysis;
import com.ondemandmonitoring.planning.dto.NormalityAssessment;
import com.ondemandmonitoring.planning.dto.OutlierSummary;
import com.ondemandmonitoring.planning.dto.PairedHypothesisTestResult;
import com.ondemandmonitoring.planning.dto.PairedScenarioObservation;
import com.ondemandmonitoring.planning.dto.PlanningExperimentResult;
import com.ondemandmonitoring.planning.dto.PlanningResearchAnalysis;
import com.ondemandmonitoring.planning.dto.PlanningStatisticalAnalysis;
import com.ondemandmonitoring.planning.dto.PlanningTimeDistributionComparison;
import com.ondemandmonitoring.planning.service.PlanningStatisticalAnalysisService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.function.Function;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.distribution.TDistribution;
import org.springframework.stereotype.Service;

@Service
public class PlanningStatisticalAnalysisServiceImpl implements PlanningStatisticalAnalysisService {

    private static final double ALPHA = 0.05;
    private static final double EPSILON = 1.0e-9;
    private static final int BOOTSTRAP_ITERATIONS = 10_000;
    private static final long BOOTSTRAP_SEED = 20260920L;
    private static final String NORMALITY_METHOD = "Jarque-Bera normality test using sample skewness and kurtosis";
    private static final String TEST_RULE = "If normality is not rejected at alpha=0.05 use two-sided paired t-test; otherwise use two-sided Wilcoxon signed-rank normal approximation.";

    @Override
    public PlanningStatisticalAnalysis analyze(
            PlanningExperimentResult experimentResult,
            PlanningResearchAnalysis researchAnalysis) {
        if (experimentResult == null) throw new IllegalArgumentException("Experiment result is required.");
        if (researchAnalysis == null) throw new IllegalArgumentException("Research analysis is required.");
        long started = System.nanoTime();
        List<PairedScenarioObservation> observations = researchAnalysis.shortestVsEnergyAware().observations();

        MetricStatisticalAnalysis energy = analyzeMetric(
                "energySavingMah",
                "Predicted energy saving",
                "mAh",
                "Positive means ENERGY_AWARE predicts less energy than ASTAR_SHORTEST.",
                true,
                values(observations, PairedScenarioObservation::energySavingMah));
        ConfidenceInterval energyPercentCi = bootstrapCi(
                values(observations, PairedScenarioObservation::energySavingPercent),
                BOOTSTRAP_ITERATIONS,
                BOOTSTRAP_SEED);

        List<MetricStatisticalAnalysis> secondary = new ArrayList<>();
        secondary.add(analyzeMetric("distanceDifferenceM", "Planned distance difference", "m",
                "Positive means ENERGY_AWARE route is longer.",
                false, values(observations, PairedScenarioObservation::distanceDifferenceM)));
        secondary.add(analyzeMetric("durationDifferenceSec", "Predicted duration difference", "sec",
                "Negative means ENERGY_AWARE predicted mission duration is shorter.",
                false, values(observations, PairedScenarioObservation::durationDifferenceSec)));
        secondary.add(analyzeMetric("altitudeDifferenceM", "Planned Gazebo World Z difference", "m",
                "Negative means ENERGY_AWARE uses lower planned Gazebo World Z.",
                false, values(observations, PairedScenarioObservation::altitudeDifferenceM)));
        secondary.add(analyzeMetric("planningTimeDifferenceMs", "Planning time difference", "ms",
                "Positive means ENERGY_AWARE takes longer to plan.",
                false, values(observations, value -> value.planningTimeDifferenceMs() == null
                        ? null : value.planningTimeDifferenceMs().doubleValue())));
        secondary = applyHolm(secondary);

        PlanningTimeDistributionComparison timing = new PlanningTimeDistributionComparison(
                distribution(values(observations, value -> value.shortestPlanningTimeMs() == null
                        ? null : value.shortestPlanningTimeMs().doubleValue())),
                distribution(values(observations, value -> value.energyAwarePlanningTimeMs() == null
                        ? null : value.energyAwarePlanningTimeMs().doubleValue())),
                distribution(values(observations, value -> value.planningTimeDifferenceMs() == null
                        ? null : value.planningTimeDifferenceMs().doubleValue())));

        return new PlanningStatisticalAnalysis(
                experimentResult.definition().experimentId(),
                experimentResult.definition().scenarios().size(),
                observations.size(),
                ALPHA,
                EPSILON,
                NORMALITY_METHOD,
                TEST_RULE,
                BOOTSTRAP_ITERATIONS,
                BOOTSTRAP_SEED,
                energy,
                energyPercentCi,
                secondary,
                timing,
                elapsedMs(started));
    }

    private MetricStatisticalAnalysis analyzeMetric(
            String key, String display, String unit, String sign, boolean primary, List<Double> values) {
        MetricDistributionSummary distribution = distribution(values);
        NormalityAssessment normality = normality(distribution);
        PairedHypothesisTestResult test = normality.rejectNormality() ? wilcoxon(values) : pairedT(values);
        EffectSizeResult effect = normality.rejectNormality()
                ? new EffectSizeResult("rank-biserial correlation", rankBiserial(values))
                : new EffectSizeResult("Cohen's dz", cohensDz(distribution));
        ConfidenceInterval ci = bootstrapCi(values, BOOTSTRAP_ITERATIONS, BOOTSTRAP_SEED);
        return new MetricStatisticalAnalysis(key, display, unit, sign, primary, distribution, normality,
                test, effect, ci, test.pValue(), null, false);
    }

    private List<MetricStatisticalAnalysis> applyHolm(List<MetricStatisticalAnalysis> analyses) {
        List<MetricStatisticalAnalysis> sorted = analyses.stream()
                .sorted(Comparator.comparing(MetricStatisticalAnalysis::rawPValue,
                        Comparator.nullsLast(Double::compareTo)))
                .toList();
        java.util.Map<String, Double> adjusted = new java.util.HashMap<>();
        double previous = 0.0;
        int m = sorted.size();
        for (int i = 0; i < sorted.size(); i++) {
            MetricStatisticalAnalysis analysis = sorted.get(i);
            double raw = analysis.rawPValue() == null ? 1.0 : analysis.rawPValue();
            double value = Math.min(1.0, Math.max(previous, (m - i) * raw));
            adjusted.put(analysis.metricKey(), value);
            previous = value;
        }
        List<MetricStatisticalAnalysis> result = new ArrayList<>();
        for (MetricStatisticalAnalysis analysis : analyses) {
            double p = adjusted.get(analysis.metricKey());
            result.add(new MetricStatisticalAnalysis(
                    analysis.metricKey(), analysis.displayName(), analysis.unit(), analysis.signConvention(),
                    analysis.primaryEndpoint(), analysis.distribution(), analysis.normality(),
                    analysis.hypothesisTest(), analysis.effectSize(), analysis.meanConfidenceInterval(),
                    analysis.rawPValue(), p, p <= ALPHA));
        }
        return result;
    }

    private MetricDistributionSummary distribution(List<Double> source) {
        List<Double> values = source.stream().filter(Objects::nonNull).filter(Double::isFinite).sorted().toList();
        int n = values.size();
        if (n == 0) {
            return new MetricDistributionSummary(0, null, null, null, null, null, null, null, null, null, null,
                    new OutlierSummary(null, null, 0), List.of());
        }
        double mean = mean(values);
        Double sd = n < 2 ? null : Math.sqrt(values.stream().mapToDouble(v -> Math.pow(v - mean, 2)).sum() / (n - 1));
        double median = median(values);
        double q1 = median(values.subList(0, n / 2));
        double q3 = median(values.subList((n + 1) / 2, n));
        double iqr = q3 - q1;
        double lowerFence = q1 - 1.5 * iqr;
        double upperFence = q3 + 1.5 * iqr;
        int outliers = (int) values.stream().filter(v -> v < lowerFence || v > upperFence).count();
        Double skewness = n < 3 || sd == null || Math.abs(sd) <= EPSILON ? null
                : values.stream().mapToDouble(v -> Math.pow((v - mean) / sd, 3)).sum() * n / ((n - 1.0) * (n - 2.0));
        Double kurtosis = n < 4 || sd == null || Math.abs(sd) <= EPSILON ? null : excessKurtosis(values, mean, sd);
        return new MetricDistributionSummary(n, mean, median, sd, q1, q3, iqr,
                values.get(0), values.get(n - 1), skewness, kurtosis,
                new OutlierSummary(lowerFence, upperFence, outliers), values);
    }

    private Double excessKurtosis(List<Double> values, double mean, double sd) {
        int n = values.size();
        double sum4 = values.stream().mapToDouble(v -> Math.pow((v - mean) / sd, 4)).sum();
        return (n * (n + 1.0) * sum4 / ((n - 1.0) * (n - 2.0) * (n - 3.0)))
                - (3.0 * Math.pow(n - 1.0, 2) / ((n - 2.0) * (n - 3.0)));
    }

    private NormalityAssessment normality(MetricDistributionSummary distribution) {
        if (distribution.n() < 4 || distribution.skewness() == null || distribution.kurtosis() == null) {
            return new NormalityAssessment(NORMALITY_METHOD, null, null, ALPHA, true);
        }
        double skew = distribution.skewness();
        double kurt = distribution.kurtosis();
        double statistic = distribution.n() / 6.0 * (skew * skew + (kurt * kurt) / 4.0);
        double p = 1.0 - new ChiSquaredDistribution(2).cumulativeProbability(statistic);
        return new NormalityAssessment(NORMALITY_METHOD, statistic, p, ALPHA, p < ALPHA);
    }

    private PairedHypothesisTestResult pairedT(List<Double> source) {
        List<Double> values = finite(source);
        MetricDistributionSummary distribution = distribution(values);
        if (values.size() < 2 || distribution.sampleStandardDeviation() == null
                || Math.abs(distribution.sampleStandardDeviation()) <= EPSILON) {
            return new PairedHypothesisTestResult("Two-sided paired t-test", "two-sided",
                    values.size(), zeroCount(values), values.size(), null,
                    values.size() - 1, null, ALPHA, false, "zeros retained in differences",
                    "not applicable");
        }
        double t = distribution.mean() / (distribution.sampleStandardDeviation() / Math.sqrt(values.size()));
        TDistribution td = new TDistribution(values.size() - 1);
        double p = 2.0 * (1.0 - td.cumulativeProbability(Math.abs(t)));
        return new PairedHypothesisTestResult("Two-sided paired t-test", "two-sided",
                values.size(), zeroCount(values), values.size(), t,
                values.size() - 1, p, ALPHA, p <= ALPHA,
                "zeros retained in differences", "not applicable");
    }

    private PairedHypothesisTestResult wilcoxon(List<Double> source) {
        List<Double> values = finite(source);
        List<Double> nonZero = values.stream().filter(v -> Math.abs(v) > EPSILON).toList();
        List<RankedDifference> ranked = ranks(nonZero);
        double positiveRank = ranked.stream().filter(r -> r.value() > 0).mapToDouble(RankedDifference::rank).sum();
        double negativeRank = ranked.stream().filter(r -> r.value() < 0).mapToDouble(RankedDifference::rank).sum();
        double statistic = Math.min(positiveRank, negativeRank);
        int n = nonZero.size();
        if (n == 0) {
            return new PairedHypothesisTestResult("Two-sided Wilcoxon signed-rank test (normal approximation)",
                    "two-sided", values.size(), values.size(), 0, 0.0, null, 1.0, ALPHA, false,
                    "approximately zero differences excluded from ranking",
                    "average ranks assigned to tied absolute differences");
        }
        double mean = n * (n + 1.0) / 4.0;
        double variance = n * (n + 1.0) * (2.0 * n + 1.0) / 24.0 - tieCorrection(ranked) / 48.0;
        double z = (positiveRank - mean) / Math.sqrt(variance);
        double p = 2.0 * (1.0 - new NormalDistribution().cumulativeProbability(Math.abs(z)));
        return new PairedHypothesisTestResult("Two-sided Wilcoxon signed-rank test (normal approximation)",
                "two-sided", values.size(), values.size() - n, n, z, null, p, ALPHA, p <= ALPHA,
                "approximately zero differences excluded from ranking",
                "average ranks assigned to tied absolute differences");
    }

    private double tieCorrection(List<RankedDifference> ranked) {
        java.util.Map<Double, Long> counts = new java.util.HashMap<>();
        for (RankedDifference r : ranked) counts.merge(Math.abs(r.value()), 1L, Long::sum);
        return counts.values().stream().filter(c -> c > 1).mapToDouble(c -> c * (c + 1.0) * (2.0 * c + 1.0)).sum();
    }

    private List<RankedDifference> ranks(List<Double> values) {
        List<Double> sorted = values.stream().sorted(Comparator.comparingDouble(Math::abs)).toList();
        List<RankedDifference> ranked = new ArrayList<>();
        int i = 0;
        while (i < sorted.size()) {
            int j = i + 1;
            while (j < sorted.size() && Math.abs(Math.abs(sorted.get(j)) - Math.abs(sorted.get(i))) <= EPSILON) j++;
            double rank = ((i + 1.0) + j) / 2.0;
            for (int k = i; k < j; k++) ranked.add(new RankedDifference(sorted.get(k), rank));
            i = j;
        }
        return ranked;
    }

    private Double cohensDz(MetricDistributionSummary distribution) {
        if (distribution.sampleStandardDeviation() == null || Math.abs(distribution.sampleStandardDeviation()) <= EPSILON) {
            return null;
        }
        return distribution.mean() / distribution.sampleStandardDeviation();
    }

    private Double rankBiserial(List<Double> source) {
        List<Double> nonZero = finite(source).stream().filter(v -> Math.abs(v) > EPSILON).toList();
        if (nonZero.isEmpty()) return 0.0;
        List<RankedDifference> ranked = ranks(nonZero);
        double positive = ranked.stream().filter(r -> r.value() > 0).mapToDouble(RankedDifference::rank).sum();
        double negative = ranked.stream().filter(r -> r.value() < 0).mapToDouble(RankedDifference::rank).sum();
        return (positive - negative) / (nonZero.size() * (nonZero.size() + 1.0) / 2.0);
    }

    private ConfidenceInterval bootstrapCi(List<Double> source, int iterations, long seed) {
        List<Double> values = finite(source);
        if (values.isEmpty()) return new ConfidenceInterval(0.95, null, null, "paired percentile bootstrap for mean", iterations, seed);
        Random random = new Random(seed);
        double[] means = new double[iterations];
        for (int i = 0; i < iterations; i++) {
            double sum = 0.0;
            for (int j = 0; j < values.size(); j++) {
                sum += values.get(random.nextInt(values.size()));
            }
            means[i] = sum / values.size();
        }
        java.util.Arrays.sort(means);
        return new ConfidenceInterval(0.95, percentile(means, 0.025), percentile(means, 0.975),
                "paired percentile bootstrap for mean", iterations, seed);
    }

    private double percentile(double[] sorted, double p) {
        double index = p * (sorted.length - 1);
        int low = (int) Math.floor(index);
        int high = (int) Math.ceil(index);
        if (low == high) return sorted[low];
        double weight = index - low;
        return sorted[low] * (1.0 - weight) + sorted[high] * weight;
    }

    private List<Double> values(List<PairedScenarioObservation> observations,
            Function<PairedScenarioObservation, Double> getter) {
        return observations.stream().map(getter).toList();
    }

    private List<Double> finite(List<Double> values) {
        return values.stream().filter(Objects::nonNull).filter(Double::isFinite).toList();
    }

    private int zeroCount(List<Double> values) {
        return (int) finite(values).stream().filter(v -> Math.abs(v) <= EPSILON).count();
    }

    private double mean(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
    }

    private double median(List<Double> sorted) {
        if (sorted.isEmpty()) return Double.NaN;
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }

    private long elapsedMs(long started) {
        return Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
    }

    private record RankedDifference(double value, double rank) {
    }
}
