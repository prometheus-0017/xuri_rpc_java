package com.xuri.rpc;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地对象代理管理器，对应TS版本的ObjectOfProxyManager。
 * 管理被暴露为远程代理的本地对象。
 * 使用IdentityHashMap确保按引用（而非equals）识别对象。
 * 线程安全：使用synchronized保护IdentityHashMap（非线程安全）。
 */
public class ObjectOfProxyManager {
    // obj -> id (按引用识别)
    private final Map<Object, String> proxyMap;
    // id -> handler
    private final ConcurrentHashMap<String, ProxyObjectHandler> reverseProxyMap;

    public ObjectOfProxyManager() {
        this.proxyMap = Collections.synchronizedMap(new IdentityHashMap<Object, String>());
        this.reverseProxyMap = new ConcurrentHashMap<String, ProxyObjectHandler>();
    }

    public synchronized void set(Object obj, String id) {
        proxyMap.put(obj, id);
        reverseProxyMap.put(id, new ProxyObjectHandler(id, obj));
    }

    public void reRegister(String id) {
        ProxyObjectHandler handler = reverseProxyMap.get(id);
        if (handler != null) {
            handler.setLastRegistered(System.currentTimeMillis());
        }
    }

    public Object getById(String id) {
        ProxyObjectHandler handler = reverseProxyMap.get(id);
        return handler != null ? handler.getTarget() : null;
    }

    public synchronized String get(Object obj) {
        return proxyMap.get(obj);
    }

    public synchronized boolean has(Object obj) {
        return proxyMap.containsKey(obj);
    }

    public synchronized void deleteById(String id) {
        ProxyObjectHandler handler = reverseProxyMap.get(id);
        if (handler != null) {
            proxyMap.remove(handler.getTarget());
            reverseProxyMap.remove(id);
        }
    }

    public synchronized void delete(Object obj) {
        String id = proxyMap.remove(obj);
        if (id != null) {
            reverseProxyMap.remove(id);
        }
    }

    public Map<String, ProxyObjectHandler> getReverseProxyMap() {
        return reverseProxyMap;
    }

    public int size() {
        return proxyMap.size();
    }

    /**
     * 内部类，持有代理对象的引用和最后注册时间。
     */
    public static class ProxyObjectHandler {
        private final String id;
        private final Object target;
        private volatile long lastRegistered;

        public ProxyObjectHandler(String id, Object target) {
            this.id = id;
            this.target = target;
            this.lastRegistered = System.currentTimeMillis();
        }

        public String getId() { return id; }
        public Object getTarget() { return target; }
        public long getLastRegistered() { return lastRegistered; }
        public void setLastRegistered(long lastRegistered) { this.lastRegistered = lastRegistered; }
    }
}
