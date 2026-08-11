package com.xuri.rpc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

/**
 * Null处理测试，对应TS版本的null.test.ts和null_in_dict_test.ts。
 * 主对象是真正的Java对象；可能为null的参数使用包装类型声明。
 */
class NullTest {

    public static class NullArgService {
        public int add(Integer a, Integer b, Object callback) {
            assertNull(callback, "callback should be null");
            return a + b;
        }
    }

    @Test
    void testNullArgument() throws Exception {
        TestHelper.mainFunc(new NullArgService(), new TestHelper.TestProcess() {
            public void run(Client client, Object main, String serverId) throws Exception {
                RemoteProxyObject proxy = (RemoteProxyObject) main;
                Object result = proxy.invoke("add", 1, 2, null);
                assertEquals(3, ((Number) result).intValue());
            }
        });
    }

    public static class NullDictService {
        public int checkNull(Map<String, Object> pack) {
            assertNull(pack.get("b"), "b should be null");
            assertNull(pack.get("c"), "c should be null");
            return ((Number) pack.get("a")).intValue() + 1;
        }
    }

    @Test
    void testNullInDict() throws Exception {
        TestHelper.mainFunc(new NullDictService(), new TestHelper.TestProcess() {
            public void run(Client client, Object main, String serverId) throws Exception {
                RemoteProxyObject proxy = (RemoteProxyObject) main;
                Map<String, Object> pack = new LinkedHashMap<String, Object>();
                pack.put("a", 1);
                pack.put("b", null);
                pack.put("c", null);
                Object result = proxy.invoke("checkNull", pack);
                assertEquals(2, ((Number) result).intValue());
            }
        });
    }

    public static class MixedNullService {
        public int mixedNull(Map<String, Object> pack) {
            assertEquals(10, ((Number) pack.get("x")).intValue());
            assertNull(pack.get("y"));
            assertEquals(30, ((Number) pack.get("z")).intValue());
            return ((Number) pack.get("x")).intValue() + ((Number) pack.get("z")).intValue();
        }
    }

    @Test
    void testMixedNullInDict() throws Exception {
        TestHelper.mainFunc(new MixedNullService(), new TestHelper.TestProcess() {
            public void run(Client client, Object main, String serverId) throws Exception {
                RemoteProxyObject proxy = (RemoteProxyObject) main;
                Map<String, Object> pack = new LinkedHashMap<String, Object>();
                pack.put("x", 10);
                pack.put("y", null);
                pack.put("z", 30);
                Object result = proxy.invoke("mixedNull", pack);
                assertEquals(40, ((Number) result).intValue());
            }
        });
    }
}
