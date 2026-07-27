package com.xuri.rpc;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * RPC客户端，对应TS版本的Client。
 * 代表一个RPC端点（可以是客户端或服务端）。
 * 线程安全：关键操作使用同步机制。
 */
public class Client {
    private volatile String hostId;
    private volatile ISender sender;
    private final ArgTranslator argTranslator = new ArgTranslator();
    private volatile AutoWrapper argsAutoWrapper;

    public interface AutoWrapper {
        Object wrap(Object obj);
    }

    private static final AutoWrapper DEFAULT_AUTO_WRAPPER = new AutoWrapper() {
        public Object wrap(Object obj) { return obj; }
    };

    public Client() {
        this(null);
    }

    public Client(String hostId) {
        this.hostId = hostId;
        this.argsAutoWrapper = DEFAULT_AUTO_WRAPPER;
    }

    public void setSender(ISender sender) {
        this.sender = sender;
    }

    public ISender getSender() {
        return sender;
    }

    public void setArgsAutoWrapper(AutoWrapper autoWrapper) {
        this.argsAutoWrapper = autoWrapper;
    }

    public String getHostId() {
        if (hostId == null) {
            return RpcFramework.getOrCreateOption(null).getHostId();
        }
        return hostId;
    }

    public ObjectOfProxyManager getProxyManager() {
        return RpcFramework.getOrCreateOption(getHostId()).getObjectOfProxyManager();
    }

    public RemoteProxyManager getRunnableProxyManager() {
        return RpcFramework.getOrCreateOption(getHostId()).getRunnableProxyManager();
    }

    public Map<String, MessageReceiverOptions.PendingRequest> getReqPending() {
        return RpcFramework.getOrCreateOption(getHostId()).getRequestPendingDict();
    }

    /**
     * 将对象转为代理。
     * 对应TS版本的asProxy。
     * 区分对象类型：
     * - CallableObject → 生成带__call__的代理描述
     * - Map → 视为数据字典，不生成代理
     * - 其他对象 → 生成包含公开方法的代理描述
     */
    public PreArgObj asProxy(Object obj) {
        return asProxy(obj, getHostId());
    }

    public static PreArgObj asProxy(Object obj, String hostIdFrom) {
        String hid = (hostIdFrom != null) ? hostIdFrom : RpcFramework.getOrCreateOption(null).getHostId();
        if (hid == null) {
            throw new RuntimeException("hostId is null");
        }

        String id = getOrGenerateObjectId(obj, hid);
        ProxyDescriber proxyDesc = createProxyForObject(id, obj, hid);
        return new PreArgObj(ArgObjType.PROXY, proxyDesc.toMap());
    }

    private static String getOrGenerateObjectId(Object obj, String hostIdFrom) {
        ObjectOfProxyManager proxyManager = RpcFramework.getOrCreateOption(hostIdFrom).getObjectOfProxyManager();
        if (!proxyManager.has(obj)) {
            String id = RpcFramework.generateId(hostIdFrom);
            proxyManager.set(obj, id);
        }
        return proxyManager.get(obj);
    }

    /**
     * 为对象创建代理描述符。
     * 区分对象类型：
     * - CallableObject → 函数代理（__call__）
     * - 普通对象 → 方法代理（列出公开方法）
     */
    private static ProxyDescriber createProxyForObject(String proxyId, Object obj, String hostId) {
        if (obj instanceof CallableObject) {
            // 可调用对象 → 生成__call__成员
            List<MemberInfo> members = new ArrayList<MemberInfo>();
            members.add(new MemberInfo("function", "__call__"));
            return new ProxyDescriber(proxyId, hostId, members);
        }

        // 普通对象 → 列出所有公开方法（排除Object类的方法和__开头的方法）
        List<MemberInfo> members = new ArrayList<MemberInfo>();
        Set<String> seen = new HashSet<String>();
        Class<?> clazz = obj.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Method m : clazz.getDeclaredMethods()) {
                String name = m.getName();
                if (name.startsWith("__") || name.startsWith("$") || seen.contains(name)) continue;
                if (!java.lang.reflect.Modifier.isPublic(m.getModifiers())) continue;
                seen.add(name);
                members.add(new MemberInfo("function", name));
            }
            clazz = clazz.getSuperclass();
        }
        // 也包含接口方法
        for (Class<?> iface : obj.getClass().getInterfaces()) {
            for (Method m : iface.getDeclaredMethods()) {
                String name = m.getName();
                if (name.startsWith("__") || name.startsWith("$") || seen.contains(name)) continue;
                seen.add(name);
                members.add(new MemberInfo("function", name));
            }
        }
        return new ProxyDescriber(proxyId, hostId, members);
    }

    /**
     * 序列化对象为可传输格式。
     */
    public Object toArgObj(Object obj) {
        return argTranslator.toArgObj(obj, new ArgTranslator.AsProxyFunction() {
            public PreArgObj asProxy(Object o) {
                return Client.this.asProxy(o);
            }
        });
    }

    /**
     * 反序列化传输格式为Java对象。
     */
    public Object reverseToArgObj(Object obj) {
        return argTranslator.reverseToArgObj(obj, this);
    }

    /**
     * 发送请求并等待响应。
     * 对应TS版本的waitForRequest。
     * Java版本使用CompletableFuture实现异步等待。
     */
    public CompletableFuture<Object> waitForRequest(Request request) {
        ISender s = this.sender;
        if (s == null) {
            CompletableFuture<Object> f = new CompletableFuture<Object>();
            f.completeExceptionally(new RuntimeException("sender not set"));
            return f;
        }

        final CompletableFuture<Object> future = new CompletableFuture<Object>();
        getReqPending().put(request.getId(),
                new MessageReceiverOptions.PendingRequest(future, request, System.currentTimeMillis()));

        try {
            s.send(request);
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    /**
     * 创建远程代理对象。
     * 使用RemoteProxyObject代替java.lang.reflect.Proxy，
     * 因为Java无法在运行时动态确定代理实现的接口。
     */
    public Object createRemoteProxy(ProxyDescriber data) {
        // 如果是本地hostId的代理，直接返回本地对象
        if (data.getHostId() != null && data.getHostId().equals(this.getHostId())) {
            return this.getProxyManager().getById(data.getId());
        }

        // 检查是否已有缓存的远程代理
        Object cached = this.getRunnableProxyManager().get(data.getId());
        if (cached != null) {
            return cached;
        }

        // 检查是否是纯函数代理（只有__call__）
        boolean hasOnlyCall = false;
        boolean hasOtherMethods = false;
        for (MemberInfo member : data.getMembers()) {
            if ("__call__".equals(member.getName())) {
                hasOnlyCall = true;
            } else {
                hasOtherMethods = true;
            }
        }

        if (hasOnlyCall && !hasOtherMethods) {
            // 纯函数代理 → 返回RemoteCallable
            return new RemoteCallable(data.getId(), this);
        }

        // 通用代理 → 返回RemoteProxyObject
        return new RemoteProxyObject(data.getId(), this);
    }

    /**
     * 获取远程主对象。
     */
    public CompletableFuture<Object> getMain() {
        return getObject("main");
    }

    /**
     * 获取指定ID的远程对象。
     */
    public CompletableFuture<Object> getObject(String objectId) {
        List<Object> args = new ArrayList<Object>();
        args.add(toArgObj(objectId));
        Request request = new Request(
                RpcFramework.generateId(this.getHostId()),
                new HashMap<String, Object>(),
                "getMain",
                "main0",
                args
        );
        return waitForRequest(request);
    }

    /**
     * 远程调用处理器。
     * 当在远程代理上调用方法时，实际发送RPC请求。
     */
    Object remoteInvoke(String proxyId, String methodName, Object[] args) throws Exception {
        List<Object> transformedArgs = new ArrayList<Object>();
        if (args != null) {
            for (Object arg : args) {
                Object wrapped = argsAutoWrapper.wrap(arg);
                transformedArgs.add(toArgObj(wrapped));
            }
        }

        Request request = new Request(
                RpcFramework.generateId(this.getHostId()),
                new HashMap<String, Object>(),
                methodName,
                proxyId,
                transformedArgs
        );

        CompletableFuture<Object> future = waitForRequest(request);
        return future.get(); // 阻塞等待结果
    }

    /**
     * 远程可调用对象，对应TS版本中只有__call__的函数代理。
     * 用户通过call()方法调用远程函数。
     */
    public static class RemoteCallable extends RemoteProxyObject {
        RemoteCallable(String proxyId, Client client) {
            super(proxyId, client);
        }

        public Object call(Object... args) throws Exception {
            return invoke("__call__", args);
        }

        @Override
        public String toString() {
            return "RemoteCallable[" + getProxyId() + "]";
        }
    }
}
