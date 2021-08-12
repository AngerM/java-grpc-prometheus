// Copyright 2016 Dino Wernli. All Rights Reserved. See LICENSE for licensing terms.
// Updates copyright 2021 Matthew Anger

package dev.angerm.grpc.prometheus.integration;

import java.util.Enumeration;
import java.util.Optional;
import java.util.Set;

import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;

/** Testing utilities used to deal with {@link CollectorRegistry} instances. */
public class RegistryHelper {
    public static Optional<Collector.MetricFamilySamples> findRecordedMetric(
        String name, CollectorRegistry collectorRegistry) {
        var results = collectorRegistry.filteredMetricFamilySamples(Set.of(name));
        if (results.hasMoreElements()) {
            return Optional.of(results.nextElement());
        }
        return Optional.empty();
    }

    public static Collector.MetricFamilySamples findRecordedMetricOrThrow(
        String name, CollectorRegistry collectorRegistry) {
        Optional<Collector.MetricFamilySamples> result = findRecordedMetric(name, collectorRegistry);
        if (!result.isPresent()){
            throw new IllegalArgumentException("Could not find metric with name: " + name);
        }
        return result.get();
    }

    public static double extractMetricValue(String name, CollectorRegistry collectorRegistry) {
        Collector.MetricFamilySamples result = findRecordedMetricOrThrow(name, collectorRegistry);
        if (result.samples.size() != 1) {
            throw new IllegalArgumentException(String.format(
                "Expected one value, but got %d for metric %s", result.samples.size(), name));
        }
        return result.samples.get(0).value;
    }

    public static int countSamples(
        String metricName, String sampleName, CollectorRegistry collectorRegistry) {
        Enumeration<Collector.MetricFamilySamples> samples = collectorRegistry.metricFamilySamples();
        while (samples.hasMoreElements()) {
            Collector.MetricFamilySamples sample = samples.nextElement();
            if (sample.name.equals(metricName)) {
                int result = 0;
                for (Collector.MetricFamilySamples.Sample s : sample.samples) {
                    if (s.name.equals(sampleName)) {
                        ++result;
                    }
                }
                return result;
            }
        }
        throw new IllegalArgumentException("Could not find sample family with name: " + metricName);
    }

    public static void printRegistry(CollectorRegistry collectorRegistry) {
        Enumeration<Collector.MetricFamilySamples> samples = collectorRegistry.metricFamilySamples();
        while (samples.hasMoreElements()) {
            printSamples(samples.nextElement());
        }
    }

    private static void printSamples(Collector.MetricFamilySamples samples) {
        System.out.println(samples.toString());
    }
}