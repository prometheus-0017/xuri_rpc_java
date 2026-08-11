package com.xuri.rpc;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 消息接收器，对应TS版本的MessageReceiver。
 * 处理接收到的RPC请求和响应。
 * 线程安全：使用ConcurrentHashMap和synchronized。
 */
public class MessageReceiver {
    private volatile String hostId;
    private Object rpcServer;
    private final List<Interceptor> interceptors = new ArrayList<Interceptor>();
    private final Set<String> objectWithContext = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private volatile AutoWrapper resultAutoWrapper;

    public interface AutoWrapper {
        Object wrap(Object obj);
    }

    private static final AutoWrapper DEFAULT_AUTO_WRAPPER = new AutoWrapper() {
        public Object wrap(Object obj) { return obj; }
    };

    public interface Interceptor {
        void intercept(RpcContext context, Request message, Client client, NextFunction next) throws Exception;
    }

    public interface NextFunction {
        void call() throws Exception;
    }

    public static class RpcContext {
        private Object value;
        private final Map<String, Object> data = new HashMap<String, Object>();

        public void setValue(Object value) { this.value = value; }
        public Object getValue() { return value; }
        public void put(String key, Object val) { data.put(key, val); }
        public Object get(String key) { return data.get(key); }
    }

    public MessageReceiver() {
        this(null);
    }

    public MessageReceiver(String hostId) {
        this.hostId = hostId;
        this.resultAutoWrapper = DEFAULT_AUTO_WRAPPER;
        // 注册main0对象
        final String hostIdToSend = getHostId();
        final MessageReceiver self = this;
        Map<String, Object> main0 = new HashMap<String, Object>();
        main0.put("getMain", new CallableObject(new CallableObject.RpcFunction() {
            public Object call(Object... args) {
                String objectId = (args.length > 0 && args[0] != null) ? String.valueOf(args[0]) : "main";
                return Client.asProxy(self.getProxyManager().getById(objectId), hostIdToSend);
            }
        }));
        main0.put("reRegister", new CallableObject(new CallableObject.RpcFunction() {
            public Object call(Object... args) {
                if (args.length > 0 && args[0] instanceof List) {
                    List<?> list = (List<?>) args[0];
                    for (Object item : list) {
                        String objectId;
                        if (item instanceof List) {
                            List<?> tuple = (List<?>) item;
                            objectId = String.valueOf(tuple.get(0));
                        } else {
                            objectId = String.valueOf(item);
                        }
                        self.getProxyManager().reRegister(objectId);
                    }
                }
                return null;
            }
        }));
        getProxyManager().set(main0, "main0");
    }

    public void setMain(Object obj) {
        this.rpcServer = obj;
        setObject("main", obj, false);
    }

    public void setObject(String id, Object obj, boolean withContext) {
        getProxyManager().set(obj, id);
        if (withContext) {
            objectWithContext.add(id);
        }
    }

    public void addInterceptor(Interceptor interceptor) {
        synchronized (interceptors) {
            interceptors.add(interceptor);
        }
    }

    public void setResultAutoWrapper(AutoWrapper autoWrapper) {
        this.resultAutoWrapper = autoWrapper;
    }

    public String getHostId() {
        return RpcFramework.getOrCreateOption(hostId).getHostId();
    }

    public ObjectOfProxyManager getProxyManager() {
        return RpcFramework.getOrCreateOption(hostId).getObjectOfProxyManager();
    }

    public RemoteProxyManager getRunnableProxyManager() {
        return RpcFramework.getOrCreateOption(hostId).getRunnableProxyManager();
    }

    public ConcurrentHashMap<String, MessageReceiverOptions.PendingRequest> getReqPending() {
        return RpcFramework.getOrCreateOption(hostId).getRequestPendingDict();
    }

    public int currentWaitingCount() {
        return getReqPending().size();
    }

    /**
     * 处理接收到的消息（请求或响应）。
     * 线程安全：此方法可能被多线程并发调用。
     */
    public void onReceiveMessage(Object messageRecv, Client clientForCallBack) throws Exception {
        if (clientForCallBack == null) {
            throw new IllegalArgumentException("clientForCallBack must not null");
        }
        if (!(clientForCallBack instanceof Client)) {
            throw new IllegalArgumentException("clientForCallBack must be a Client");
        }

        if (isResponse(messageRecv)) {
            handleResponse((Map<String, Object>) messageRecv, clientForCallBack);
        } else {
            handleRequest(messageRecv, clientForCallBack);
        }
    }

    private boolean isResponse(Object msg) {
        if (msg instanceof Map) {
            return ((Map<?, ?>) msg).containsKey("idFor") && ((Map<?, ?>) msg).get("idFor") != null;
        }
        if (msg instanceof Response) {
            return ((Response) msg).getIdFor() != null;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private void handleRequest(Object messageRecv, Client clientForCallBack) {
        Request message;
        if (messageRecv instanceof Request) {
            message = (Request) messageRecv;
        } else if (messageRecv instanceof Map) {
            message = mapToRequest((Map<String, Object>) messageRecv);
        } else {
            return;
        }

        try {
            Object object = getProxyManager().getById(message.getObjectId());
            if (object == null) {
                clientForCallBack.getSender().send(
                        RpcFramework.generateErrorReply(message, "object not found", 100, getHostId()));
                return;
            }

            // 反序列化参数
            List<Object> args = new ArrayList<Object>();
            for (Object arg : message.getArgs()) {
                args.add(clientForCallBack.reverseToArgObj(arg));
            }

            // 调用方法
            Object result;
            boolean shouldWithContext = objectWithContext.contains(message.getObjectId());
            String method = message.getMethod();

            if ("__call__".equals(method)) {
                if (shouldWithContext) {
                    result = withContext(message, clientForCallBack, args, object);
                } else {
                    result = invokeCallable(object, args.toArray());
                }
            } else {
                if (shouldWithContext) {
                    Method targetMethod = findMethod(object, method, args.toArray());
                    result = withContext(message, clientForCallBack, args, targetMethod);
                } else {
                    result = invokeMethod(object, method, args.toArray());
                }
            }

            // 序列化结果
            result = resultAutoWrapper.wrap(result);
            Object wrappedResult = clientForCallBack.toArgObj(result);

            // 发送响应
            Response response = new Response();
            response.setId(RpcFramework.generateId(getHostId()));
            response.setIdFor(message.getId());
            response.setMeta(new HashMap<String, Object>());
            response.setData(wrappedResult);
            response.setStatus(200);
            clientForCallBack.getSender().send(response);

        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            String traceStr = sw.toString();

            Response errorResponse = new Response();
            errorResponse.setId(RpcFramework.generateId(getHostId()));
            errorResponse.setIdFor(message.getId());
            errorResponse.setMeta(new HashMap<String, Object>());
            errorResponse.setData(null);
            errorResponse.setTrace(traceStr);
            errorResponse.setStatus(-1);
            try {
                clientForCallBack.getSender().send(errorResponse);
            } catch (Exception sendEx) {
                System.err.println("Failed to send error response: " + sendEx.getMessage());
            }
            System.err.println("Error handling request: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void handleResponse(Map<String, Object> messageMap, Client clientForCallBack) {
        String idFor = (String) messageMap.get("idFor");
        ConcurrentHashMap<String, MessageReceiverOptions.PendingRequest> reqPending = getReqPending();
        MessageReceiverOptions.PendingRequest req = reqPending.remove(idFor);
        if (req == null) {
            return;
        }

        int status = messageMap.get("status") instanceof Number ?
                ((Number) messageMap.get("status")).intValue() : 0;

        if (status == 200) {
            Object data = messageMap.get("data");
            Object result = clientForCallBack.reverseToArgObj(data);
            req.getFuture().complete(result);
        } else {
            String trace = (String) messageMap.get("trace");
            req.getFuture().completeExceptionally(new RpcException(status, trace));
        }
    }

    /**
     * 拦截器链执行。
     */
    private Object withContext(Request message, Client client, List<Object> args, Object target) throws Exception {
        final RpcContext context = new RpcContext();
        final Object[] result = new Object[1];

        // 构建拦截器链
        List<Interceptor> interceptorList;
        synchronized (interceptors) {
            interceptorList = new ArrayList<Interceptor>(interceptors);
        }

        // 最终的执行函数（调用实际方法）
        NextFunction finalNext = new NextFunction() {
            public void call() throws Exception {
                if (target instanceof Method) {
                    Method m = (Method) target;
                    Object obj = getProxyManager().getById(message.getObjectId());
                    result[0] = m.invoke(obj, RemoteObjectAdapter.adaptArgs(m, args.toArray()));
                } else {
                    result[0] = invokeCallable(target, args.toArray());
                }
            }
        };

        // 从后往前构建拦截器链
        NextFunction current = finalNext;
        for (int i = interceptorList.size() - 1; i >= 0; i--) {
            final Interceptor interceptor = interceptorList.get(i);
            final NextFunction next = current;
            current = new NextFunction() {
                public void call() throws Exception {
                    interceptor.intercept(context, message, client, next);
                }
            };
        }

        current.call();
        return result[0];
    }

    /**
     * 调用可调用对象。
     */
    private Object invokeCallable(Object obj, Object[] args) throws Exception {
        if (obj instanceof CallableObject) {
            return ((CallableObject) obj).getFunction().call(args);
        }
        // 如果对象本身是Map且包含__call__键
        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            Object callObj = map.get("__call__");
            if (callObj instanceof CallableObject) {
                return ((CallableObject) callObj).getFunction().call(args);
            }
        }
        // 尝试通过反射调用call方法
        try {
            Method callMethod = obj.getClass().getMethod("call", Object[].class);
            return callMethod.invoke(obj, (Object) args);
        } catch (NoSuchMethodException e) {
            // 尝试invoke方法
            try {
                Method invokeMethod = obj.getClass().getMethod("invoke", Object[].class);
                return invokeMethod.invoke(obj, (Object) args);
            } catch (NoSuchMethodException e2) {
                throw new RuntimeException("Object is not callable: " + obj.getClass().getName());
            }
        }
    }

    /**
     * 通过反射调用对象的方法。
     * 参数会先经过RemoteObjectAdapter适配，使普通Java对象无需改动即可接收远程对象。
     */
    private Object invokeMethod(Object obj, String methodName, Object[] args) throws Exception {
        Method method = findMethod(obj, methodName, args);
        if (method == null) {
            // 尝试在Map中查找
            if (obj instanceof Map) {
                Object func = ((Map<?, ?>) obj).get(methodName);
                if (func instanceof CallableObject) {
                    return ((CallableObject) func).getFunction().call(args);
                }
                throw new RuntimeException("Method not found: " + methodName);
            }
            throw new RuntimeException("Method not found: " + methodName + " on " + obj.getClass().getName());
        }
        return method.invoke(obj, RemoteObjectAdapter.adaptArgs(method, args));
    }

    /**
     * 查找匹配的方法。
     * 优先选择参数可适配的重载，其次是参数个数相同的重载。
     */
    private Method findMethod(Object obj, String methodName, Object[] args) {
        if (obj instanceof Map) {
            return null; // Map不使用反射
        }
        int argCount = (args != null) ? args.length : 0;
        Method sameArity = null;
        Method nameMatched = null;
        for (Method m : obj.getClass().getMethods()) {
            if (!m.getName().equals(methodName)) continue;
            if (nameMatched == null) nameMatched = m;
            if (m.getParameterTypes().length != argCount) continue;
            if (RemoteObjectAdapter.isAdaptable(m.getParameterTypes(), args)) {
                return m;
            }
            if (sameArity == null) sameArity = m;
        }
        return (sameArity != null) ? sameArity : nameMatched;
    }

    /**
     * 将Map转换为Request对象。
     */
    @SuppressWarnings("unchecked")
    private Request mapToRequest(Map<String, Object> map) {
        Request req = new Request();
        req.setId((String) map.get("id"));
        req.setMethod((String) map.get("method"));
        req.setObjectId((String) map.get("objectId"));
        Object metaObj = map.get("meta");
        if (metaObj instanceof Map) {
            req.setMeta((Map<String, Object>) metaObj);
        } else {
            req.setMeta(new HashMap<String, Object>());
        }
        Object argsObj = map.get("args");
        if (argsObj instanceof List) {
            req.setArgs((List<Object>) argsObj);
        } else {
            req.setArgs(new ArrayList<Object>());
        }
        return req;
    }
}
