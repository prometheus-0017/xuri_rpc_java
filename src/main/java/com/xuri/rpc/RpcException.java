package com.xuri.rpc;

/**
 * RPC异常，包含状态码和追踪信息。
 * 对应TS版本中reject时传递的Response对象。
 */
public class RpcException extends Exception {
    private final int status;
    private final String trace;

    public RpcException(int status, String trace) {
        super("RPC error (status=" + status + "): " + trace);
        this.status = status;
        this.trace = trace;
    }

    public int getStatus() { return status; }
    public String getTrace() { return trace; }
}
