// Copyright 2016 Dino Wernli. All Rights Reserved. See LICENSE for licensing terms.
// Updates copyright 2021 Matthew Anger

package dev.angerm.grpc.prometheus.integration;

import com.google.common.collect.ImmutableList;
import io.grpc.Channel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.protobuf.services.HealthStatusManager;
import io.grpc.stub.AbstractStub;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.StreamRecorder;
import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.CollectorRegistry;
import dev.angerm.grpc.prometheus.Configuration;
import dev.angerm.grpc.prometheus.MonitoringServerInterceptor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.google.common.truth.Truth.assertThat;

/**
 * Integrations tests which make sure that if a service is started with a
 * {@link MonitoringServerInterceptor}, then all Prometheus metrics get recorded correctly.
 */
public class MonitoringServerInterceptorIntegrationTest {
  private static final String grpcServerName = "grpc-server";
  private static final HealthCheckRequest REQUEST = HealthCheckRequest.newBuilder()
      .build();

  private static final Configuration CHEAP_METRICS = Configuration.cheapMetricsOnly();
  private static final Configuration ALL_METRICS = Configuration.allMetrics();

  private CollectorRegistry collectorRegistry;
  private Server grpcServer;

  @Before
  public void setUp() {
    collectorRegistry = new CollectorRegistry();
  }

  @After
  public void tearDown() throws Exception {
    grpcServer.shutdown().awaitTermination();
  }

  @Test
  public void unaryRpcMetrics() throws Throwable {
    startGrpcServer(CHEAP_METRICS);
    createGrpcBlockingStub().check(REQUEST);

    assertThat(findRecordedMetricOrThrow("grpc_server_started_total").samples).hasSize(1);

    MetricFamilySamples handled = findRecordedMetricOrThrow("grpc_server_handled_total");
    assertThat(handled.samples).hasSize(1);
    assertThat(handled.samples.get(0).labelValues).containsExactly(
        "UNARY", HealthGrpc.SERVICE_NAME, HealthGrpc.getCheckMethod().getBareMethodName(),
        "OK", "OK"); // TODO: These are the "code" and "grpc_code" labels which are currently duplicated. "code" should be deprecated in a future release.
    assertThat(handled.samples.get(0).value).isWithin(0).of(1);
  }

  @Test
  public void noHistogramIfDisabled() throws Throwable {
    startGrpcServer(CHEAP_METRICS);
    createGrpcBlockingStub().check(REQUEST);
    assertThat(RegistryHelper.findRecordedMetric(
        "grpc_server_handled_latency_seconds", collectorRegistry).isPresent()).isFalse();
  }

  @Test
  public void addsHistogramIfEnabled() throws Throwable {
    startGrpcServer(ALL_METRICS);
    createGrpcBlockingStub().check(REQUEST);

    MetricFamilySamples latency = findRecordedMetricOrThrow("grpc_server_handled_latency_seconds");
    assertThat(latency.samples.size()).isGreaterThan(0);
  }

  @Test
  public void overridesHistogramBuckets() throws Throwable {
    double[] buckets = new double[] {0.1, 0.2, 0.8};
    startGrpcServer(ALL_METRICS.withLatencyBuckets(buckets));
    createGrpcBlockingStub().check(REQUEST);

    long expectedNum = buckets.length + 1;  // Our two buckets and the Inf buckets.
    assertThat(countSamples(
        "grpc_server_handled_latency_seconds",
        "grpc_server_handled_latency_seconds_bucket")).isEqualTo(expectedNum);
  }

  @Test
  public void recordsMultipleCalls() throws Throwable {
    startGrpcServer(CHEAP_METRICS);

    createGrpcBlockingStub().check(REQUEST);
    createGrpcBlockingStub().check(REQUEST);
    createGrpcBlockingStub().check(REQUEST);

    assertThat(findRecordedMetricOrThrow("grpc_server_started_total").samples).hasSize(2);
    assertThat(findRecordedMetricOrThrow("grpc_server_handled_total").samples).hasSize(2);
  }

  @Test
  public void recordsCustomHeaders() throws Throwable {
    startGrpcServer(CHEAP_METRICS, List.of("MY_HEADER"));

    createGrpcBlockingStub().check(REQUEST);
    createGrpcBlockingStub().check(REQUEST);
    createGrpcBlockingStub().check(REQUEST);

    var startedTotal = findRecordedMetricOrThrow("grpc_server_started_total").samples.get(0);
    assertThat(startedTotal.labelNames.contains("MY_HEADER"));
    assertThat(startedTotal.labelValues.contains("UNKNOWN"));
    assertThat(startedTotal.value == 3.0);
    var handledTotal = findRecordedMetricOrThrow("grpc_server_handled_total").samples.get(0);
    assertThat(handledTotal.labelNames.contains("MY_HEADER"));
    assertThat(handledTotal.labelValues.contains("UNKNOWN"));
    assertThat(handledTotal.value == 3.0);

    var metadata = new Metadata();
    metadata.put(Metadata.Key.of("MY_HEADER", Metadata.ASCII_STRING_MARSHALLER), "MY_VALUE");
    var metaStub = MetadataUtils.attachHeaders(
        createGrpcBlockingStub(),
        metadata
    );
    metaStub.check(REQUEST);
    var newStartedTotal = findRecordedMetricOrThrow("grpc_server_started_total").samples.get(1);
    assertThat(newStartedTotal.labelNames.contains("MY_HEADER"));
    assertThat(newStartedTotal.labelValues.contains("MY_VALUE"));
    assertThat(newStartedTotal.value == 1.0);
    var newHandledTotal = findRecordedMetricOrThrow("grpc_server_handled_total").samples.get(1);
    assertThat(newHandledTotal.labelNames.contains("MY_HEADER"));
    assertThat(newHandledTotal.labelValues.contains("MY_VALUE"));
    assertThat(newHandledTotal.value == 1.0);
  }

  private void startGrpcServer(Configuration monitoringConfig) {
    startGrpcServer(monitoringConfig, Collections.emptyList());
  }
  private void startGrpcServer(Configuration monitoringConfig, List<String> headers) {
    MonitoringServerInterceptor interceptor = MonitoringServerInterceptor.create(
        monitoringConfig
            .withCollectorRegistry(collectorRegistry)
            .withHeadersToLog(headers)
    );
    var manager =  new HealthStatusManager();
    grpcServer = InProcessServerBuilder.forName(grpcServerName)
        .addService(ServerInterceptors.intercept(manager.getHealthService(), interceptor))
        .build();
    try {
      grpcServer.start();
    } catch (IOException e) {
      throw new RuntimeException("Exception while running grpc server", e);
    }
  }

  private MetricFamilySamples findRecordedMetricOrThrow(String name) {
    return RegistryHelper.findRecordedMetricOrThrow(name, collectorRegistry);
  }

  private HealthGrpc.HealthBlockingStub createGrpcBlockingStub() {
    return HealthGrpc.newBlockingStub(createGrpcChannel());
  }

  private int countSamples(String metricName, String sampleName) {
    return RegistryHelper.countSamples(metricName, sampleName, collectorRegistry);
  }

  private HealthGrpc.HealthStub createGrpcStub() {
    return HealthGrpc.newStub(createGrpcChannel());
  }

  private Channel createGrpcChannel() {
    return InProcessChannelBuilder.forName(grpcServerName)
        .usePlaintext()
        .build();
  }
}
