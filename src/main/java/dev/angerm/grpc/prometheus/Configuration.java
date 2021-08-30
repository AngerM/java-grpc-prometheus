// Copyright 2016 Dino Wernli. All Rights Reserved. See LICENSE for licensing terms.

package dev.angerm.grpc.prometheus;

import io.prometheus.client.CollectorRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds information about which metrics should be kept track of during rpc calls. Can be used to
 * turn on more elaborate and expensive metrics, such as latency histograms.
 */
public class Configuration {
  private static double[] DEFAULT_LATENCY_BUCKETS =
      new double[] {.001, .005, .01, 0.025, .05, 0.075, .1, .2, .3, .4, .5, 0.75, 1, 2, 5, 10, 20};

  private final boolean isIncludeLatencyHistograms;
  private final CollectorRegistry collectorRegistry;
  private final double[] latencyBuckets;
  private final List<String> headersToLog;

  /** Returns a {@link Configuration} for recording all cheap metrics about the rpcs. */
  public static Configuration cheapMetricsOnly() {
    return new Configuration(
        false /* isIncludeLatencyHistograms */,
        CollectorRegistry.defaultRegistry,
        DEFAULT_LATENCY_BUCKETS, null);
  }

  /**
   * Returns a {@link Configuration} for recording all metrics about the rpcs. This includes
   * metrics which might produce a lot of data, such as latency histograms.
   */
  public static Configuration allMetrics() {
    return new Configuration(
        true /* isIncludeLatencyHistograms */,
        CollectorRegistry.defaultRegistry,
        DEFAULT_LATENCY_BUCKETS, null);
  }

  /**
   * Returns a copy {@link Configuration} with the difference that Prometheus metrics are
   * recorded using the supplied {@link CollectorRegistry}.
   */
  public Configuration withCollectorRegistry(CollectorRegistry collectorRegistry) {
    return new Configuration(isIncludeLatencyHistograms, collectorRegistry, latencyBuckets, headersToLog);
  }

  /**
   * Returns a copy {@link Configuration} with the difference that the latency histogram values are
   * recorded with the specified set of buckets.
   */
  public Configuration withLatencyBuckets(double[] buckets) {
    return new Configuration(isIncludeLatencyHistograms, collectorRegistry, buckets, headersToLog);
  }

  /**
   * Returns a copy {@link Configuration} overriding the headers to log with the specified value.
   */
  public Configuration withHeadersToLog(List<String> headers) {
    return new Configuration(isIncludeLatencyHistograms, collectorRegistry, latencyBuckets, headers);
  }

  /** Returns whether or not latency histograms for calls should be included. */
  public boolean isIncludeLatencyHistograms() {
    return isIncludeLatencyHistograms;
  }

  /** Returns the {@link CollectorRegistry} used to record stats. */
  public CollectorRegistry getCollectorRegistry() {
    return collectorRegistry;
  }

  /** Returns the histogram buckets to use for latency metrics. */
  public double[] getLatencyBuckets() {
    return latencyBuckets;
  }

  public List<String> getHeadersToLog() {
    return headersToLog;
  }

  private Configuration(
      boolean isIncludeLatencyHistograms,
      CollectorRegistry collectorRegistry,
      double[] latencyBuckets, List<String> headersToLog) {
    this.isIncludeLatencyHistograms = isIncludeLatencyHistograms;
    this.collectorRegistry = collectorRegistry;
    this.latencyBuckets = latencyBuckets;
    this.headersToLog = headersToLog;
  }
}
