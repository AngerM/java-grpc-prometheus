// Copyright 2016 Dino Wernli. All Rights Reserved. See LICENSE for licensing terms.

package dev.angerm.grpc.prometheus;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

/** A {@link ServerInterceptor} which sends stats about incoming grpc calls to Prometheus. */
public class MonitoringServerInterceptor implements ServerInterceptor {
  private final Clock clock;
  private final Configuration configuration;
  private final ServerMetrics.Factory serverMetricsFactory;

  public static MonitoringServerInterceptor create(Configuration configuration) {
    return new MonitoringServerInterceptor(
        Clock.systemDefaultZone(), configuration, new ServerMetrics.Factory(configuration));
  }

  private MonitoringServerInterceptor(
      Clock clock, Configuration configuration, ServerMetrics.Factory serverMetricsFactory) {
    this.clock = clock;
    this.configuration = configuration;
    this.serverMetricsFactory = serverMetricsFactory;
  }

  @Override
  public <R, S> ServerCall.Listener<R> interceptCall(
      ServerCall<R, S> call,
      Metadata requestHeaders,
      ServerCallHandler<R, S> next) {
    MethodDescriptor<R, S> methodDescriptor = call.getMethodDescriptor();
    GrpcMethod grpcMethod = GrpcMethod.of(methodDescriptor);
    var headers = customHeadersToLog(requestHeaders);
    ServerMetrics metrics = serverMetricsFactory.createMetricsForMethod(grpcMethod, headers);
    ServerCall<R,S> monitoringCall = new MonitoringServerCall(call, clock, grpcMethod, metrics, configuration);
    return new MonitoringServerCallListener<>(
        next.startCall(monitoringCall, requestHeaders), metrics, grpcMethod);
  }

  private List<String> customHeadersToLog(Metadata requestHeaders) {
    var returnVal = new ArrayList<String>();
    List<String> headers = configuration.getHeadersToLog();
    // keep these in the same order as they are listed in the config
    // for simplicity when adding all the labels to metrics
    for (String header: headers) {
      var value = requestHeaders.get(Metadata.Key.of(header, Metadata.ASCII_STRING_MARSHALLER));
      returnVal.add(value);
    }
    return returnVal;
  }
}
