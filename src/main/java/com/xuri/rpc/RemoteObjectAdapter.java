package com.xuri.rpc;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;

/**
 * 远程对象适配器。
 * 让未经改造的普通Java对象也能直接接收远程对象：
 * 当方法参数（或接口方法返回值）声明为某个接口，而实际值是远程代理时，
 * 自动生成实现该接口的JDK动态代理，把接口方法调用转发为远程调用。
 *
 * 适配规则：
 * - 值本身已符合目标类型 → 原样传递（如声明为Object、RemoteProxyObject、Map等）
 * - 远程代理 + 接口类型 → 生成动态代理，方法名直接转发
 * - RemoteCallable + 函数式接口 → 生成动态代理，唯一抽象方法映射到__call__
 * - 数字类型 → 按目标类型做宽化转换（JSON传输后统一是Integer/Double）
 *
 * 由于JDK动态代理只能实现接口，参数声明为具体类时无法适配。
 */
public class RemoteObjectAdapter {

    /**
     * 按方法签名适配整个参数列表。
     */
    public static Object[] adaptArgs(Method method, Object[] args) {
        Object[] safeArgs = (args != null) ? args : new Object[0];
        Class<?>[] paramTypes = method.getParameterTypes();
        if (paramTypes.length != safeArgs.length) {
            return safeArgs; // 参数个数不匹配，交由反射报错
        }
        Object[] adapted = new Object[safeArgs.length];
        for (int i = 0; i < safeArgs.length; i++) {
            adapted[i] = adapt(safeArgs[i], paramTypes[i]);
        }
        return adapted;
    }

    /**
     * 将单个值适配为目标类型可接受的形式。
     */
    public static Object adapt(Object value, Class<?> targetType) {
        if (targetType == null || targetType == Object.class || value == null) {
            return value;
        }
        if (isInstanceOf(value, targetType)) {
            return value;
        }
        if (value instanceof RemoteProxyObject && targetType.isInterface()) {
            return asInterface((RemoteProxyObject) value, targetType);
        }
        Object coerced = coerce(value, targetType);
        return (coerced != null) ? coerced : value;
    }

    /**
     * 判断参数列表是否能适配到目标签名，用于重载方法的选择。
     */
    public static boolean isAdaptable(Class<?>[] paramTypes, Object[] args) {
        Object[] safeArgs = (args != null) ? args : new Object[0];
        if (paramTypes.length != safeArgs.length) {
            return false;
        }
        for (int i = 0; i < safeArgs.length; i++) {
            if (!isAdaptable(safeArgs[i], paramTypes[i])) {
                return false;
            }
        }
        return true;
    }

    public static boolean isAdaptable(Object value, Class<?> targetType) {
        if (targetType == null || targetType == Object.class) {
            return true;
        }
        if (value == null) {
            return !targetType.isPrimitive();
        }
        if (isInstanceOf(value, targetType)) {
            return true;
        }
        if (value instanceof RemoteProxyObject) {
            return targetType.isInterface();
        }
        return coerce(value, targetType) != null;
    }

    /**
     * 为远程代理生成实现指定接口的动态代理。
     */
    public static Object asInterface(RemoteProxyObject remote, Class<?> interfaceType) {
        if (!interfaceType.isInterface()) {
            throw new IllegalArgumentException(
                    "Cannot adapt remote object to " + interfaceType.getName() + ": only interfaces are supported");
        }
        // RemoteCallable只有__call__成员，需要把函数式接口的唯一抽象方法映射过去
        String callableMethodName = null;
        if (remote instanceof Client.RemoteCallable) {
            Method sam = findSingleAbstractMethod(interfaceType);
            if (sam != null) {
                callableMethodName = sam.getName();
            }
        }
        ClassLoader loader = interfaceType.getClassLoader();
        if (loader == null) {
            loader = RemoteObjectAdapter.class.getClassLoader();
        }
        return Proxy.newProxyInstance(loader, new Class<?>[]{interfaceType},
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

    private static boolean isInstanceOf(Object value, Class<?> targetType) {
        Class<?> type = targetType.isPrimitive() ? wrapperOf(targetType) : targetType;
        return type.isInstance(value);
    }

    /**
     * 基本类型之间的转换，无法转换时返回null。
     */
    private static Object coerce(Object value, Class<?> targetType) {
        Class<?> type = targetType.isPrimitive() ? wrapperOf(targetType) : targetType;
        if (value instanceof Number) {
            Number num = (Number) value;
            if (type == Integer.class) return Integer.valueOf(num.intValue());
            if (type == Long.class) return Long.valueOf(num.longValue());
            if (type == Double.class) return Double.valueOf(num.doubleValue());
            if (type == Float.class) return Float.valueOf(num.floatValue());
            if (type == Short.class) return Short.valueOf(num.shortValue());
            if (type == Byte.class) return Byte.valueOf(num.byteValue());
        }
        if (value instanceof String && type == Character.class && ((String) value).length() == 1) {
            return Character.valueOf(((String) value).charAt(0));
        }
        return null;
    }

    private static Class<?> wrapperOf(Class<?> primitiveType) {
        if (primitiveType == int.class) return Integer.class;
        if (primitiveType == long.class) return Long.class;
        if (primitiveType == double.class) return Double.class;
        if (primitiveType == float.class) return Float.class;
        if (primitiveType == short.class) return Short.class;
        if (primitiveType == byte.class) return Byte.class;
        if (primitiveType == boolean.class) return Boolean.class;
        if (primitiveType == char.class) return Character.class;
        return primitiveType; // void
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
            return adapt(result, returnType);
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
