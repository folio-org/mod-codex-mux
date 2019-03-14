package org.folio.codex;

import java.util.Map;

import io.vertx.core.Context;

public class MergeRequest <T> {

  private int offset;
  private int limit;
  private Map<String, String> headers;
  private Context vertxContext;
  private Map<String, Multiplexer.MuxCollection<T>> muxCollectionMap;

  public int getOffset() {
    return offset;
  }

  public int getLimit() {
    return limit;
  }

  public Map<String, String> getHeaders() {
    return headers;
  }

  public Context getVertxContext() {
    return vertxContext;
  }

  public Map<String, Multiplexer.MuxCollection<T>> getMuxCollectionMap() {
    return muxCollectionMap;
  }

  private MergeRequest(MergeRequestBuilder<T> builder) {
    this.offset = builder.offset;
    this.limit = builder.limit;
    this.headers = builder.headers;
    this.vertxContext = builder.vertxContext;
    this.muxCollectionMap = builder.muxCollectionMap;
  }

  public static class MergeRequestBuilder<T> {

    private int offset;
    private int limit;
    private Map<String, String> headers;
    private Context vertxContext;
    private Map<String, Multiplexer.MuxCollection<T>> muxCollectionMap;


    public MergeRequestBuilder<T> setOffset(int offset) {
      this.offset = offset;
      return this;
    }

    public MergeRequestBuilder<T> setLimit(int limit) {
      this.limit = limit;
      return this;
    }

    public MergeRequestBuilder<T> setHeaders(Map<String, String> headers) {
      this.headers = headers;
      return this;
    }

    public MergeRequestBuilder<T> setVertxContext(Context vertxContext) {
      this.vertxContext = vertxContext;
      return this;
    }

    public MergeRequestBuilder<T> setMuxCollectionMap(Map<String, Multiplexer.MuxCollection<T>> muxCollectionMap) {
      this.muxCollectionMap = muxCollectionMap;
      return this;
    }

    public MergeRequest<T> build() {
      return new MergeRequest<>(this);
    }
  }
}
