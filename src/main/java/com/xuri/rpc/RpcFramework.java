package com.xuri.rpc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * RPC框架全局工具和配置，对应TS版本的模块级全局状态。
 * 线程安全：使用ConcurrentHashMap和volatile。
 */
public class RpcFramework {
    private static volatile String hostId = null;
    private static volatile boolean debugFlag = false;
    private static volatile boolean autoConvertDataToObject = false;
    private static final AtomicLong idCounter = new AtomicLong(0);

    /** 每个hostId对应一个MessageReceiverOptions */
    private static final ConcurrentHashMap<String, MessageReceiverOptions> options =
            new ConcurrentHashMap<String, MessageReceiverOptions>();
    private static final String DEFAULT_HOST_KEY = "__default__";

    public static void setHostId(String id) {
        hostId = id;
        getOrCreateOption(null).setHostId(id);
    }

    public static String getHostId() {
        return hostId;
    }

    public static void setDebugFlag(boolean flag) {
        debugFlag = flag;
    }

    public static boolean getDebugFlag() {
        return debugFlag;
    }

    /**
     * 设置是否自动将字典/列表转换为Java对象。
     * 启用后，在invoke之前会尝试将Map转换为目标Java对象，将List转换为目标数组或集合。
     */
    public static void setAutoConvertDataToObject(boolean flag) {
        autoConvertDataToObject = flag;
    }

    public static boolean getAutoConvertDataToObject() {
        return autoConvertDataToObject;
    }

    public static String generateId(String hostIdParam) {
        String hid = (hostIdParam != null) ? hostIdParam : hostId;
        if (hid == null) hid = "";
        return hid + "" + idCounter.getAndIncrement();
    }

    public static MessageReceiverOptions getOrCreateOption(String id) {
        String key;
        if (id == null || id.equals(hostId)) {
            key = DEFAULT_HOST_KEY;
        } else {
            key = id;
        }

        MessageReceiverOptions opt = options.get(key);
        if (opt == null) {
            opt = new MessageReceiverOptions();
            opt.setHostId(key.equals(DEFAULT_HOST_KEY) ? null : key);
            MessageReceiverOptions existing = options.putIfAbsent(key, opt);
            if (existing != null) {
                opt = existing;
            }
        }
        return opt;
    }

    public static void deleteProxyById(String id, String hostIdParam) {
        getOrCreateOption(hostIdParam).getObjectOfProxyManager().deleteById(id);
    }

    public static void deleteProxy(Object obj, String hostIdParam) {
        getOrCreateOption(hostIdParam).getObjectOfProxyManager().delete(obj);
    }

    /**
     * 生成错误回复。
     */
    public static Response generateErrorReply(Request message, String errorText, int status, String hostIdParam) {
        Response reply = new Response();
        reply.setId(generateId(hostIdParam));
        reply.setIdFor(message.getId());
        reply.setMeta(new HashMap<String, Object>());
        reply.setTrace(errorText);
        reply.setStatus(status);
        return reply;
    }

    public static Response generateErrorReply(Request message, String errorText) {
        return generateErrorReply(message, errorText, 500, null);
    }

    /**
     * 移除过期的代理对象。
     */
    public static void removeOutdatedProxyObject(long timeoutMs) {
        if (timeoutMs <= 0) {
            timeoutMs = 30000;
        }
        for (Map.Entry<String, MessageReceiverOptions> entry : options.entrySet()) {
            ObjectOfProxyManager manager = entry.getValue().getObjectOfProxyManager();
            int count = 0;
            for (Map.Entry<String, ObjectOfProxyManager.ProxyObjectHandler> e : manager.getReverseProxyMap().entrySet()) {
                String id = e.getKey();
                ObjectOfProxyManager.ProxyObjectHandler handler = e.getValue();
                if ("main0".equals(id)) continue;
                if (System.currentTimeMillis() - handler.getLastRegistered() > timeoutMs * 3) {
                    manager.deleteById(id);
                    count++;
                }
            }
        }
    }

    /**
     * 获取代理持有信息。
     */
    public static List<Map<String, Object>> getProxyHoldingInfo() {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (MessageReceiverOptions v : options.values()) {
            ObjectOfProxyManager manager = v.getObjectOfProxyManager();
            long earliestDate = Long.MAX_VALUE;
            for (Map.Entry<String, ObjectOfProxyManager.ProxyObjectHandler> e : manager.getReverseProxyMap().entrySet()) {
                if ("main0".equals(e.getKey())) continue;
                if (e.getValue() != null && e.getValue().getLastRegistered() < earliestDate) {
                    earliestDate = e.getValue().getLastRegistered();
                }
            }
            Map<String, Object> info = new HashMap<String, Object>();
            info.put("hostId", v.getHostId());
            info.put("count", manager.size());
            info.put("earliestDate", earliestDate == Long.MAX_VALUE ? 0L : earliestDate);
            result.add(info);
        }
        return result;
    }

    /**
     * 清除所有全局状态（用于测试隔离）。
     */
    public static void reset() {
        options.clear();
        idCounter.set(0);
        hostId = null;
        debugFlag = false;
        autoConvertDataToObject = false;
    }
}
