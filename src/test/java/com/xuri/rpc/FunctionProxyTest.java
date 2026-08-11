package com.xuri.rpc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 函数代理测试，对应TS版本的function_proxy.test.ts。
 * 测试CallableObject的远程调用（__call__模式）。
 * 主对象是真正的Java对象，其方法返回函数代理。
 */
class FunctionProxyTest {

    public static class MultiplierService {
        private final String hostId;

        public MultiplierService(String hostId) {
            this.hostId = hostId;
        }

        public Object getMultiplier() {
            CallableObject fn = new CallableObject(new CallableObject.RpcFunction() {
                public Object call(Object... innerArgs) {
                    int a = ((Number) innerArgs[0]).intValue();
                    int b = ((Number) innerArgs[1]).intValue();
                    return a * b;
                }
            });
            return Client.asProxy(fn, hostId);
        }
    }

    @Test
    void testCallableProxy() throws Exception {
        final String serverId = "funcProxyServer";
        TestHelper.mainFunc(new MultiplierService(serverId), new TestHelper.TestProcess() {
            public void run(Client client, Object main, String sid) throws Exception {
                RemoteProxyObject proxy = (RemoteProxyObject) main;
                // getMultiplier返回一个RemoteCallable
                Object multiplier = proxy.invoke("getMultiplier");
                assertTrue(multiplier instanceof Client.RemoteCallable, "should be RemoteCallable");
                Client.RemoteCallable callable = (Client.RemoteCallable) multiplier;
                Object result = callable.call(3, 4);
                assertEquals(12, ((Number) result).intValue());
            }
        }, serverId, "funcProxyClient");
    }

    public static class CounterService {
        private final String hostId;
        private final AtomicInteger callCount = new AtomicInteger(0);

        public CounterService(String hostId) {
            this.hostId = hostId;
        }

        public Object getCounter() {
            CallableObject fn = new CallableObject(new CallableObject.RpcFunction() {
                public Object call(Object... innerArgs) {
                    return callCount.incrementAndGet();
                }
            });
            return Client.asProxy(fn, hostId);
        }
    }

    @Test
    void testCallableMultipleCalls() throws Exception {
        final String serverId = "funcProxyServer2";
        TestHelper.mainFunc(new CounterService(serverId), new TestHelper.TestProcess() {
            public void run(Client client, Object main, String sid) throws Exception {
                RemoteProxyObject proxy = (RemoteProxyObject) main;
                Object counter = proxy.invoke("getCounter");
                Client.RemoteCallable callable = (Client.RemoteCallable) counter;
                assertEquals(1, ((Number) callable.call()).intValue());
                assertEquals(2, ((Number) callable.call()).intValue());
                assertEquals(3, ((Number) callable.call()).intValue());
            }
        }, serverId, "funcProxyClient2");
    }
}
