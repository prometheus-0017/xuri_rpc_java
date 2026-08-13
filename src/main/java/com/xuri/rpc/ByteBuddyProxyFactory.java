package com.xuri.rpc;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.FieldAccessor;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.This;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于ByteBuddy的远程代理工厂。
 * 相比JDK动态代理，ByteBuddy可以代理接口和具体类（非final），适用范围更广。
 */
public class ByteBuddyProxyFactory implements RemoteProxyFactory {

    /** 缓存已生成的代理类，避免重复生成字节码：targetType -> GeneratedProxy */
    private static final ConcurrentHashMap<Class<?>, Class<?>> proxyClassCache =
            new ConcurrentHashMap<Class<?>, Class<?>>();

    /** 记录每个proxyId对应的callable方法名（函数式接口的SAM映射到__call__） */
    private static final ConcurrentHashMap<String, String> callableMethodNames =
            new ConcurrentHashMap<String, String>();

    public Object createProxy(RemoteProxyObject remote, Class<?> targetType) {
        if (targetType.isInterface()) {
            return createInterfaceProxy(remote, targetType);
        }
        return createClassProxy(remote, targetType);
    }

    // ==================== 接口代理 ====================

    private Object createInterfaceProxy(RemoteProxyObject remote, Class<?> targetType) {
        registerCallableMethodName(remote, targetType);

        Class<?> proxyClass = getOrBuildProxyClass(targetType, targetType);
        Object instance = instantiate(proxyClass, remote);

        if (remote instanceof Client.RemoteCallable) {
            setCallableFieldName(instance, remote, targetType);
        }
        return instance;
    }

    // ==================== 具体类代理 ====================

    private Object createClassProxy(RemoteProxyObject remote, Class<?> targetType) {
        if (Modifier.isFinal(targetType.getModifiers())) {
            throw new IllegalArgumentException(
                    "ByteBuddy无法代理final类: " + targetType.getName());
        }

        registerCallableMethodName(remote, targetType);

        Class<?> proxyClass = getOrBuildProxyClass(targetType, targetType);
        Object instance = instantiate(proxyClass, remote);

        if (remote instanceof Client.RemoteCallable) {
            setCallableFieldName(instance, remote, targetType);
        }
        return instance;
    }

    // ==================== 公共工具方法 ====================

    /**
     * 注册callable方法名映射。
     * 当remote是RemoteCallable且目标类型有唯一抽象方法时，将该方法名映射到__call__。
     */
    private static void registerCallableMethodName(RemoteProxyObject remote, Class<?> targetType) {
        if (remote instanceof Client.RemoteCallable) {
            Method sam = findSingleAbstractMethod(targetType);
            if (sam != null) {
                callableMethodNames.put(remote.getProxyId(), sam.getName());
            }
        }
    }

    /**
     * 为实例设置callable方法名字段。
     */
    private static void setCallableFieldName(Object instance, RemoteProxyObject remote, Class<?> targetType) {
        String callableName = callableMethodNames.get(remote.getProxyId());
        if (callableName != null) {
            try {
                java.lang.reflect.Field field = instance.getClass().getDeclaredField("__callableMethodName__");
                field.setAccessible(true);
                field.set(instance, callableName);
            } catch (Exception e) {
                // 字段不存在则忽略
            }
        }
    }

    /**
     * 获取或构建代理类。
     */
    private static Class<?> getOrBuildProxyClass(Class<?> targetType, Class<?> sourceType) {
        Class<?> cached = proxyClassCache.get(sourceType);
        if (cached != null) return cached;

        DynamicType.Unloaded<?> unloaded;
        if (sourceType.isInterface()) {
            unloaded = new ByteBuddy()
                    .subclass(RemoteProxyObject.class)
                    .implement(sourceType)
                    .defineField("__callableMethodName__", String.class, Visibility.PUBLIC)
                    .method(ElementMatchers.any())
                    .intercept(MethodDelegation.to(ByteBuddyInterceptor.class))
                    .make();
        } else {
            unloaded = new ByteBuddy()
                    .subclass(sourceType)
                    .defineField("__callableMethodName__", String.class, Visibility.PUBLIC)
                    .method(ElementMatchers.any())
                    .intercept(MethodDelegation.to(ByteBuddyInterceptor.class))
                    .make();
        }

        Class<?> proxyClass = unloaded
                .load(sourceType.getClassLoader())
                .getLoaded();
        proxyClassCache.put(sourceType, proxyClass);
        return proxyClass;
    }

    /**
     * 实例化代理类，通过构造函数传入RemoteProxyObject。
     */
    private static Object instantiate(Class<?> proxyClass, RemoteProxyObject remote) {
        try {
            return proxyClass.getConstructor(String.class, Client.class)
                    .newInstance(remote.getProxyId(), remote.getClient());
        } catch (Exception e) {
            throw new RuntimeException("无法实例化ByteBuddy代理类", e);
        }
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

    // ==================== ByteBuddy拦截器 ====================

    /**
     * ByteBuddy方法委托的目标类。
     * 所有代理对象的方法调用都会转发到这里。
     */
    public static class ByteBuddyInterceptor {

        @RuntimeType
        public static Object intercept(
                @This RemoteProxyObject self,
                @Origin Method method,
                @AllArguments Object[] args
        ) throws Throwable {
            // Object方法处理
            if (method.getDeclaringClass() == Object.class) {
                String name = method.getName();
                if ("equals".equals(name)) {
                    return handleEquals(self, args[0]);
                }
                if ("hashCode".equals(name)) {
                    return self.hashCode();
                }
                if ("toString".equals(name)) {
                    return self.toString();
                }
            }

            // 解析实际远程方法名（callable SAM方法映射到__call__）
            String methodName = resolveRemoteMethodName(self, method.getName());

            // 执行远程调用
            Object[] callArgs = (args != null) ? args : new Object[0];
            Object result;
            try {
                result = self.invoke(methodName, callArgs);
            } catch (Exception e) {
                throw translateException(e, method);
            }

            // 处理返回值
            Class<?> returnType = method.getReturnType();
            if (returnType == void.class || returnType == Void.class) {
                return null;
            }
            return RemoteObjectAdapter.adapt(result, returnType);
        }

        /**
         * 解析远程方法名。
         * 如果当前方法是该代理的callable SAM方法，则映射为__call__。
         */
        private static String resolveRemoteMethodName(RemoteProxyObject self, String methodName) {
            String callableName = callableMethodNames.get(self.getProxyId());
            if (callableName != null && methodName.equals(callableName)) {
                return "__call__";
            }
            return methodName;
        }

        /**
         * 处理equals方法，支持比较不同类型的远程代理（JDK代理与ByteBuddy代理）。
         */
        private static boolean handleEquals(RemoteProxyObject self, Object other) {
            if (other == null) return false;
            if (other instanceof RemoteProxyObject) {
                return self.getProxyId().equals(((RemoteProxyObject) other).getProxyId());
            }
            // 兼容JDK动态代理的比较
            if (java.lang.reflect.Proxy.isProxyClass(other.getClass())) {
                try {
                    java.lang.reflect.InvocationHandler handler =
                            java.lang.reflect.Proxy.getInvocationHandler(other);
                    java.lang.reflect.Field remoteField =
                            handler.getClass().getDeclaredField("remote");
                    remoteField.setAccessible(true);
                    Object remote = remoteField.get(handler);
                    if (remote instanceof RemoteProxyObject) {
                        return self.getProxyId().equals(((RemoteProxyObject) remote).getProxyId());
                    }
                } catch (Exception e) {
                    // 无法获取，返回false
                }
            }
            return false;
        }

        /**
         * 远程调用抛出的异常若不在方法的声明列表中，包装为RuntimeException。
         */
        private static Throwable translateException(Exception e, Method method) {
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
