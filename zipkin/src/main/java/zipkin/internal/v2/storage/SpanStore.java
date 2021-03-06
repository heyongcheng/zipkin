/**
 * Copyright 2015-2017 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package zipkin.internal.v2.storage;

import java.util.List;
import zipkin.DependencyLink;
import zipkin.Endpoint;
import zipkin.internal.v2.Call;
import zipkin.internal.v2.Span;
import zipkin.storage.StorageComponent;

/**
 * Queries data derived from {@link SpanConsumer}.
 *
 * <p>Note: This is not considered a user-level Api, rather an Spi that can be used to bind
 * user-level abstractions such as futures or observables.
 */
public interface SpanStore {

  /**
   * Retrieves spans grouped by trace ID from the storage system with no ordering expectation.
   *
   * <p>If {@link StorageComponent.Builder#strictTraceId(boolean)} is enabled, spans with the same
   * 64-bit trace ID will be grouped together.
   */
  Call<List<List<Span>>> getTraces(QueryRequest request);

  /**
   * Retrieves spans that share a 128-bit trace id with no ordering expectation or empty if none are
   * found.
   *
   * <p>When {@link StorageComponent.Builder#strictTraceId(boolean)} is true, spans with the same
   * right-most 16 characters are returned even if the characters to the left are not.
   *
   * <p>Implementations should use {@link Span#normalizeTraceId(String)} to ensure consistency.
   *
   * @param traceId the {@link Span#traceId() trace ID}
   */
  Call<List<Span>> getTrace(String traceId);

  /**
   * Retrieves all {@link Span#localEndpoint() local} and {@link Span#remoteEndpoint() remote}
   * {@link Endpoint#serviceName service names}, sorted lexicographically.
   */
  Call<List<String>> getServiceNames();

  /**
   * Retrieves all {@link Span#name() span names} recorded by a {@link Span#localEndpoint()
   * service}, sorted lexicographically.
   */
  Call<List<String>> getSpanNames(String serviceName);

  /**
   * Returns dependency links derived from spans in an interval contained by (endTs - lookback) or
   * empty if none are found.
   *
   * <p>Implementations may bucket aggregated data, for example daily. When this is the case, endTs
   * may be floored to align with that bucket, for example midnight if daily. lookback applies to
   * the original endTs, even when bucketed. Using the daily example, if endTs was 11pm and lookback
   * was 25 hours, the implementation would query against 2 buckets.
   *
   * <p>Some implementations parse spans from storage and call {@link
   * zipkin.internal.DependencyLinker} to aggregate links. The reason is certain graph logic, such
   * as skipping up the tree is difficult to implement as a storage query.
   *
   * <p>There's no parameter to indicate how to handle mixed ID length: this operates the same as if
   * {@link StorageComponent.Builder#strictTraceId(boolean)} was set to false. This ensures call
   * counts are not incremented twice due to one hop downgrading from 128 to 64-bit trace IDs.
   *
   * @param endTs only return links from spans where {@link Span#timestamp} are at or before this
   * time in epoch milliseconds.
   * @param lookback only return links from spans where {@link Span#timestamp} are at or after
   * (endTs - lookback) in milliseconds.
   */
  Call<List<DependencyLink>> getDependencies(long endTs, long lookback);
}
