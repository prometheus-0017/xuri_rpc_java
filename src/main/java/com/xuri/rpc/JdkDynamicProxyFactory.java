package com.xuri.rpc;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;

/**
 * 基于JDK动态代理的远程代理工厂。
 * 只能实现接口类型，无法代理具体类。
 */
public class JdkDynamicProxyFactory implements RemoteProxyFactory {

    public Object createProxy(RemoteProxyObject remote, Class<?> targetType) {
        if (!targetType.isInterface()) {
            throw new IllegalArgumentException(
                    "JDK动态代理仅支持接口类型，无法代理: " + targetType.getName());
        }

        // RemoteCallable只有__call__成员，需要把函数式接口的唯一抽象方法映射过去
        String callableMethodName = null;
        if (remote instanceof Client.RemoteCallable) {
            Method sam = findSingleAbstractMethod(targetType);
            if (sam != null) {
                callableMethodName = sam.getName();
            }
        }

        ClassLoader loader = targetType.getClassLoader();
        if (loader == null) {
            loader = JdkDynamicProxyFactory.class.getClassLoader();
        }
        return Proxy.newProxyInstance(loader, new Class<?>[]{targetType},
                new RemoteInvocationHandler(remote, callableMethodName));
    }

    /**
     * 查找接口的唯一抽象方法（函数式接口的SAM），不唯一时返回null。
     */
    private static Method findSingleAbstractMethod(Class<?> interfaceType) {
        Method found = null;
        for (Method m : interfaceType.getMethods()) {
            if (!Modifier.isAbstract(m.getModifiers())) continue;
            if (m.getDeclaringClass() == Object.class) continue;
            if (found != null) return null;
            found = m;
        }
        return found;
    }

    /**
     * 把接口方法调用转发为远程调用。
     */
    private static class RemoteInvocationHandler implements InvocationHandler {
        private final RemoteProxyObject remote;
        private final String callableMethodName;

        RemoteInvocationHandler(RemoteProxyObject remote, String callableMethodName) {
            this.remote = remote;
            this.callableMethodName = callableMethodName;
        }

        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                String name = method.getName();
                if ("equals".equals(name)) {
                    return isSameRemote(args[0]);
                }
                if ("hashCode".equals(name)) {
                    return Integer.valueOf(remote.hashCode());
                }
                if ("toString".equals(name)) {
                    return remote.toString();
                }
            }

            String methodName = method.getName().equals(callableMethodName) ? "__call__" : method.getName();
            Object[] callArgs = (args != null) ? args : new Object[0];
            Object result;
            try {
                result = remote.invoke(methodName, callArgs);
            } catch (Exception e) {
                throw translate(e, method);
            }

            Class<?> returnType = method.getReturnType();
            if (returnType == void.class || returnType == Void.class) {
                return null;
            }
            return RemoteObjectAdapter.adapt(result, returnType);
        }

        private Boolean isSameRemote(Object other) {
            if (other == null || !Proxy.isProxyClass(other.getClass())) {
                return Boolean.FALSE;
            }
            InvocationHandler handler = Proxy.getInvocationHandler(other);
            if (!(handler instanceof RemoteInvocationHandler)) {
                return Boolean.FALSE;
            }
            return Boolean.valueOf(remote.equals(((RemoteInvocationHandler) handler).remote));
        }

        /**
         * 远程调用抛出的异常若不在接口方法的声明列表中，包装为RuntimeException。
         */
        private Throwable translate(Exception e, Method method) {
            if (e instanceof RuntimeException) {
                return e;
            }
            for (Class<?> declared : method.getExceptionTypes()) {
                if (declared.isInstance(e)) {
                    return e;
                }
            }
            return new RuntimeException(e);
        }
    }
}
