package com.xuri.rpc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 基础RPC测试，对应TS版本的base.test.ts。
 * 主对象是一个真正的Java对象，通过反射调用其公开方法。
 */
class BaseTest {

    public static class MainService {
        public int add(int a, int b) {
            return a + b;
        }
    }

    @Test
    void testAddTwoNumbers() throws Exception {
        TestHelper.mainFunc(new MainService(), new TestHelper.TestProcess() {
            public void run(Client client, Object main, String serverId) throws Exception {
                RemoteProxyObject proxy = (RemoteProxyObject) main;
                Object result = proxy.invoke("add", 1, 2);
                assertEquals(3, ((Number) result).intValue());
            }
        });
    }
}
