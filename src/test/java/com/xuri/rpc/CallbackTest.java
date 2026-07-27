package com.xuri.rpc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 回调测试，对应TS版本的callback.test.ts。
 * 测试远程方法中传递回调函数（通过CallableObject包装）。
 */
class CallbackTest {

    @Test
    void testCallbackArgument() throws Exception {
        Map<String, Object> mainObj = new LinkedHashMap<String, Object>();
        mainObj.put("add", new CallableObject(new CallableObject.RpcFunction() {
            public Object call(Object... args) throws Exception {
                int a = ((Number) args[0]).intValue();
                int b = ((Number) args[1]).intValue();
                // args[2] 是回调函数（RemoteCallable）
                RemoteProxyObject callback = (RemoteProxyObject) args[2];
                callback.invoke("__call__", a + b);
                return a + b;
            }
        }));

        TestHelper.mainFunc(mainObj, new TestHelper.TestProcess() {
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
