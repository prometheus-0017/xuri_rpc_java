package com.xuri.rpc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 回调测试，对应TS版本的callback.test.ts。
 * 测试远程方法中传递回调函数（通过CallableObject包装）。
 * 主对象是一个真正的Java对象，回调参数在服务端表现为RemoteCallable。
 */
class CallbackTest {

    public static class MainService {
        public int add(int a, int b, Client.RemoteCallable callback) throws Exception {
            callback.call(a + b);
            return a + b;
        }
    }

    @Test
    void testCallbackArgument() throws Exception {
        TestHelper.mainFunc(new MainService(), new TestHelper.TestProcess() {
            public void run(Client client, Object main, String serverId) throws Exception {
                RemoteProxyObject proxy = (RemoteProxyObject) main;
                final AtomicBoolean called = new AtomicBoolean(false);
                final AtomicInteger callbackValue = new AtomicInteger(0);

                // 创建回调函数
                CallableObject callback = new CallableObject(new CallableObject.RpcFunction() {
                    public Object call(Object... args) {
                        called.set(true);
                        callbackValue.set(((Number) args[0]).intValue());
                        return null;
                    }
                });
                PreArgObj callbackProxy = Client.asProxy(callback, client.getHostId());

                Object result = proxy.invoke("add", 1, 2, callbackProxy);
                assertEquals(3, ((Number) result).intValue());
                assertTrue(called.get(), "callback should have been called");
                assertEquals(3, callbackValue.get(), "callback value should be 3");
            }
        });
    }
}
