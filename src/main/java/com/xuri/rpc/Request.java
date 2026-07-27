package com.xuri.rpc;

import java.util.List;
import java.util.Map;

/**
 * RPC请求消息。
 */
public class Request {
    private String id;
    private Map<String, Object> meta;
    private String method;
    private String objectId;
    private List<Object> args;

    public Request() {
    }

    public Request(String id, Map<String, Object> meta, String method, String objectId, List<Object> args) {
        this.id = id;
        this.meta = meta;
        this.method = method;
        this.objectId = objectId;
        this.args = args;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Map<String, Object> getMeta() { return meta; }
    public void setMeta(Map<String, Object> meta) { this.meta = meta; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getObjectId() { return objectId; }
    public void setObjectId(String objectId) { this.objectId = objectId; }
    public List<Object> getArgs() { return args; }
    public void setArgs(List<Object> args) { this.args = args; }
}
