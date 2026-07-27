package com.xuri.rpc;

/**
 * 远程代理对象，对应TS版本中动态创建的代理字典。
 * 在Java中，由于无法像TS那样动态添加方法到对象，
 * 使用invoke方法来调用远程方法。
 *
 * 使用方式：
 *   RemoteProxyObject proxy = (RemoteProxyObject) main;
 *   Object result = proxy.invoke("methodName", arg1, arg2);
 */
public class RemoteProxyObject {
    private final String proxyId;
    private final Client client;

    public RemoteProxyObject(String proxyId, Client client) {
        this.proxyId = proxyId;
        this.client = client;
    }

    /**
     * 调用远程方法。
     */
    public Object invoke(String methodName, Object... args) throws Exception {
        try {
            return client.remoteInvoke(proxyId, methodName, args);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            throw new RuntimeException(cause);
        }
    }

    public String getProxyId() { return proxyId; }
    public Client getClient() { return client; }

    @Override
    public String toString() {
        return "RemoteProxy[" + proxyId + "]";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof RemoteProxyObject)) return false;
        RemoteProxyObject other = (RemoteProxyObject) obj;
        return proxyId.equals(other.proxyId);
    }

    @Override
    public int hashCode() {
        return proxyId.hashCode();
    }
}
