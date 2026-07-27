package com.xuri.rpc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 基础RPC测试，对应TS版本的base.test.ts。
 */
class BaseTest {

    @Test
    void testAddTwoNumbers() throws Exception {
        Map<String, Object> mainObj = new LinkedHashMap<String, Object>();
        mainObj.put("add", new CallableObject(new CallableObject.RpcFunction() {
            public Object call(Object... args) {
                int a = ((Number) args[0]).intValue();
                int b = ((Number) args[1]).intValue();
                return a + b;
            }
        }));

        TestHelper.mainFunc(mainObj, new TestHelper.TestProcess() {
            public void run(Client client, Object main, String serverId) throws Exception {
                RemoteProxyObject proxy = (RemoteProxyObject) main;
                Object result = proxy.invoke("add", 1, 2);
                assertEquals(3, ((Number) result).intValue());
            }
        });
    }
}
