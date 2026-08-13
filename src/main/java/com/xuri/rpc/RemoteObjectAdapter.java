package com.xuri.rpc;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * 远程对象适配器。
 * 让未经改造的普通Java对象也能直接接收远程对象：
 * 当方法参数（或接口方法返回值）声明为某个接口，而实际值是远程代理时，
 * 自动生成实现该接口的JDK动态代理，把接口方法调用转发为远程调用。
 *
 * 适配规则：
 * - 值本身已符合目标类型 → 原样传递（如声明为Object、RemoteProxyObject、Map等）
 * - 远程代理 + 接口类型 → 通过RemoteProxyFactory生成代理，方法名直接转发
 * - RemoteCallable + 函数式接口 → 通过RemoteProxyFactory生成代理，唯一抽象方法映射到__call__
 * - 数字类型 → 按目标类型做宽化转换（JSON传输后统一是Integer/Double）
 *
 * 默认的JDK动态代理工厂只能实现接口，参数声明为具体类时无法适配。
 * 切换到ByteBuddy工厂后，可以支持具体类的代理。
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
        
        // 如果启用了自动转换，尝试将Map/List转换为Java对象
        if (RpcFramework.getAutoConvertDataToObject()) {
            Object converted = tryConvertDataToObject(value, targetType);
            if (converted != null) {
                return converted;
            }
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
        // 如果启用了自动转换，检查是否可以转换
        if (RpcFramework.getAutoConvertDataToObject()) {
            if (canConvertDataToObject(value, targetType)) {
                return true;
            }
        }
        return coerce(value, targetType) != null;
    }

    /**
     * 为远程代理生成实现指定类型的代理。
     * 使用当前配置的 {@link RemoteProxyFactory} 创建代理实例。
     */
    public static Object asInterface(RemoteProxyObject remote, Class<?> targetType) {
        return RpcFramework.getRemoteProxyFactory().createProxy(remote, targetType);
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
     * 尝试将Map/List转换为Java对象。
     * 返回null表示无法转换。
     */
    private static Object tryConvertDataToObject(Object value, Class<?> targetType) {
        // Map → Java对象
        if (value instanceof Map && shouldConvertMapToObject(targetType)) {
            return convertMapToObject((Map<?, ?>) value, targetType);
        }
        
        // List → 数组
        if (value instanceof List && targetType.isArray()) {
            return convertListToArray((List<?>) value, targetType.getComponentType());
        }
        
        return null;
    }

    /**
     * 检查是否可以将Map/List转换为目标类型。
     */
    private static boolean canConvertDataToObject(Object value, Class<?> targetType) {
        if (value instanceof Map && shouldConvertMapToObject(targetType)) {
            return true;
        }
        if (value instanceof List && targetType.isArray()) {
            return true;
        }
        return false;
    }

    /**
     * 判断是否应该将Map转换为对象。
     * 排除接口、抽象类、基本类型包装类、String、集合接口等。
     */
    private static boolean shouldConvertMapToObject(Class<?> targetType) {
        if (targetType.isInterface() || Modifier.isAbstract(targetType.getModifiers())) {
            return false;
        }
        if (targetType.isPrimitive() || isWrapperType(targetType)) {
            return false;
        }
        if (targetType == String.class) {
            return false;
        }
        // 排除标准JDK类型
        if (targetType.getName().startsWith("java.")) {
            return false;
        }
        // 必须有默认构造函数
        try {
            targetType.getDeclaredConstructor();
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /**
     * 判断是否是基本类型包装类。
     */
    private static boolean isWrapperType(Class<?> type) {
        return type == Integer.class || type == Long.class || type == Double.class ||
               type == Float.class || type == Short.class || type == Byte.class ||
               type == Boolean.class || type == Character.class;
    }

    /**
     * 将Map转换为Java对象。
     */
    private static Object convertMapToObject(Map<?, ?> map, Class<?> targetType) {
        try {
            Object instance = targetType.getDeclaredConstructor().newInstance();
            
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String fieldName = String.valueOf(entry.getKey());
                Object fieldValue = entry.getValue();
                
                try {
                    Field field = targetType.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    
                    Class<?> fieldType = field.getType();
                    Object convertedValue = adapt(fieldValue, fieldType);
                    
                    // 处理基本类型的转换
                    if (fieldType.isPrimitive() && convertedValue != null) {
                        if (fieldType == int.class && convertedValue instanceof Number) {
                            convertedValue = ((Number) convertedValue).intValue();
                        } else if (fieldType == long.class && convertedValue instanceof Number) {
                            convertedValue = ((Number) convertedValue).longValue();
                        } else if (fieldType == double.class && convertedValue instanceof Number) {
                            convertedValue = ((Number) convertedValue).doubleValue();
                        } else if (fieldType == float.class && convertedValue instanceof Number) {
                            convertedValue = ((Number) convertedValue).floatValue();
                        } else if (fieldType == boolean.class && convertedValue instanceof Boolean) {
                            convertedValue = convertedValue;
                        }
                    }
                    
                    field.set(instance, convertedValue);
                } catch (NoSuchFieldException e) {
                    // 字段不存在，跳过
                }
            }
            
            return instance;
        } catch (Exception e) {
            // 转换失败，返回null
            return null;
        }
    }

    /**
     * 将List转换为数组。
     */
    private static Object convertListToArray(List<?> list, Class<?> componentType) {
        Object array = Array.newInstance(componentType, list.size());
        for (int i = 0; i < list.size(); i++) {
            Object element = adapt(list.get(i), componentType);
            Array.set(array, i, element);
        }
        return array;
    }
}
