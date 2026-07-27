package com.xuri.rpc;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 远程代理管理器，对应TS版本的RemoteProxyManager。
 * 管理从远端接收到的代理对象。
 * 线程安全：使用ConcurrentHashMap。
 */
public class RemoteProxyManager {
    // id -> proxy (使用弱引用在Java中不太适合，因为Java的WeakReference需要配合ReferenceQueue使用)
    private final ConcurrentHashMap<String, Object> map;
    // client -> set of proxy ids
    private final ConcurrentHashMap<Client, Set<String>> clientMap;

    public RemoteProxyManager() {
        this.map = new ConcurrentHashMap<String, Object>();
        this.clientMap = new ConcurrentHashMap<Client, Set<String>>();
    }

    public void set(String id, Object proxy, Client client) {
        map.put(id, proxy);
        Set<String> ids = clientMap.get(client);
        if (ids == null) {
            ids = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
            Set<String> existing = clientMap.putIfAbsent(client, ids);
            if (existing != null) {
                ids = existing;
            }
        }
        ids.add(id);
    }

    public Object get(String id) {
        return map.get(id);
    }

    public boolean has(String id) {
        return map.containsKey(id);
    }

    public void remove(String id) {
        map.remove(id);
    }

    public Map<Client, Set<String>> getClientMap() {
        return clientMap;
    }

    public int size() {
        return map.size();
    }
}
