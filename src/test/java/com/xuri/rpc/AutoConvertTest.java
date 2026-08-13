package com.xuri.rpc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

/**
 * 自动数据转换测试，测试将字典/列表自动转换为Java对象的功能。
 */
class AutoConvertTest {

    /**
     * 纯数据类，对应TS版本的纯数据对象。
     */
    public static class UserData {
        private String name;
        private int age;

        public UserData() {
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }
    }

    public static class UserService {
        public String greet(UserData user) {
            return "Hello, " + user.getName() + "! You are " + user.getAge() + " years old.";
        }
    }

    @Test
    void testMapToObject() throws Exception {
        TestHelper.mainFunc(new UserService(), new TestHelper.TestProcess() {
            public void run(Client client, Object main, String serverId) throws Exception {
                // 在测试流程内部启用自动转换
                RpcFramework.setAutoConvertDataToObject(true);
                
                try {
                    RemoteProxyObject proxy = (RemoteProxyObject) main;

                    // 使用Map传递数据，而不是创建UserData对象
                    Map<String, Object> userMap = new LinkedHashMap<String, Object>();
                    userMap.put("name", "Alice");
                    userMap.put("age", 30);

                    Object result = proxy.invoke("greet", userMap);
                    assertEquals("Hello, Alice! You are 30 years old.", result);
                } finally {
                    // 关闭自动转换
                    RpcFramework.setAutoConvertDataToObject(false);
                }
            }
        });
    }

    @Test
    void testDirectConversion() {
        // 直接测试转换逻辑，不通过RPC
        RpcFramework.setAutoConvertDataToObject(true);

        try {
            Map<String, Object> userMap = new LinkedHashMap<String, Object>();
            userMap.put("name", "Bob");
            userMap.put("age", 25);

            Object result = RemoteObjectAdapter.adapt(userMap, UserData.class);
            assertTrue(result instanceof UserData);
            UserData user = (UserData) result;
            assertEquals("Bob", user.getName());
            assertEquals(25, user.getAge());
        } finally {
            RpcFramework.setAutoConvertDataToObject(false);
        }
    }
}
