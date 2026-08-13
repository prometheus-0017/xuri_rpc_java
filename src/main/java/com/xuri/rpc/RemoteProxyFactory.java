package com.xuri.rpc;

/**
 * 远程代理工厂接口，定义如何将远程代理对象注入为目标类型的实例。
 * 不同的实现可以使用不同的字节码生成技术：
 * <ul>
 *   <li>{@link JdkDynamicProxyFactory} — 基于JDK动态代理，仅支持接口</li>
 *   <li>{@link ByteBuddyProxyFactory} — 基于ByteBuddy，支持接口和具体类</li>
 * </ul>
 */
public interface RemoteProxyFactory {

    /**
     * 为远程代理对象创建实现指定类型的实例。
     *
     * @param remote     远程代理对象，持有proxyId和client引用
     * @param targetType 需要实现的目标类型（接口或具体类）
     * @return 实现了targetType的代理实例
     */
    Object createProxy(RemoteProxyObject remote, Class<?> targetType);
}
