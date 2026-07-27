package com.xuri.rpc;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 每个hostId对应的配置选项，对应TS版本的MessageReceiverOptions。
 * 线程安全：requestPendingDict使用ConcurrentHashMap。
 */
public class MessageReceiverOptions {
    private volatile String hostId;
    private final ObjectOfProxyManager objectOfProxyManager;
    private final RemoteProxyManager runnableProxyManager;
    private final ConcurrentHashMap<String, PendingRequest> requestPendingDict;

    public MessageReceiverOptions() {
        this.objectOfProxyManager = new ObjectOfProxyManager();
        this.runnableProxyManager = new RemoteProxyManager();
        this.requestPendingDict = new ConcurrentHashMap<String, PendingRequest>();
    }

    public String getHostId() { return hostId; }
    public void setHostId(String hostId) { this.hostId = hostId; }
    public ObjectOfProxyManager getObjectOfProxyManager() { return objectOfProxyManager; }
    public RemoteProxyManager getRunnableProxyManager() { return runnableProxyManager; }
    public ConcurrentHashMap<String, PendingRequest> getRequestPendingDict() { return requestPendingDict; }

    /**
     * 挂起的请求，包含CompletableFuture和发送时间。
     */
    public static class PendingRequest {
        private final java.util.concurrent.CompletableFuture<Object> future;
        private final Object request;
        private final long sendTime;

        public PendingRequest(java.util.concurrent.CompletableFuture<Object> future, Object request, long sendTime) {
            this.future = future;
            this.request = request;
            this.sendTime = sendTime;
        }

        public java.util.concurrent.CompletableFuture<Object> getFuture() { return future; }
        public Object getRequest() { return request; }
        public long getSendTime() { return sendTime; }
    }
}
