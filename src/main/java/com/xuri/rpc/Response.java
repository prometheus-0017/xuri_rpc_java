package com.xuri.rpc;

import java.util.Map;

/**
 * RPC响应消息。
 */
public class Response {
    private String id;
    private String idFor;
    private Map<String, Object> meta;
    private int status;
    private String trace;
    private Object data;

    public Response() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getIdFor() { return idFor; }
    public void setIdFor(String idFor) { this.idFor = idFor; }
    public Map<String, Object> getMeta() { return meta; }
    public void setMeta(Map<String, Object> meta) { this.meta = meta; }
    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }
    public String getTrace() { return trace; }
    public void setTrace(String trace) { this.trace = trace; }
    public Object getData() { return data; }
    public void setData(Object data) { this.data = data; }
}
