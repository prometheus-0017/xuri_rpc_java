package com.xuri.rpc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

/**
 * 单元测试，对应TS版本的unit_api.test.ts。
 * 测试各个API的基本功能。
 */
class UnitApiTest {

    @Test
    void testObjectOfProxyManagerSetAndGet() {
        ObjectOfProxyManager manager = new ObjectOfProxyManager();
        Object obj = new Object();
        manager.set(obj, "id1");
        assertSame(obj, manager.getById("id1"));
        assertEquals("id1", manager.get(obj));
        assertTrue(manager.has(obj));
    }

    @Test
    void testObjectOfProxyManagerDeleteByRef() {
        ObjectOfProxyManager manager = new ObjectOfProxyManager();
        Object obj = new Object();
        manager.set(obj, "id2");
        assertTrue(manager.has(obj));
        manager.delete(obj);
        assertFalse(manager.has(obj));
        assertNull(manager.getById("id2"));
    }

    @Test
    void testObjectOfProxyManagerDeleteById() {
        ObjectOfProxyManager manager = new ObjectOfProxyManager();
        Object obj = new Object();
        manager.set(obj, "id3");
        manager.deleteById("id3");
        assertFalse(manager.has(obj));
        assertNull(manager.getById("id3"));
    }

    @Test
    void testObjectOfProxyManagerMultipleObjects() {
        ObjectOfProxyManager manager = new ObjectOfProxyManager();
        Object obj1 = new Object();
        Object obj2 = new Object();
        manager.set(obj1, "id1");
        manager.set(obj2, "id2");
        assertEquals("id1", manager.get(obj1));
        assertEquals("id2", manager.get(obj2));
        assertSame(obj1, manager.getById("id1"));
        assertSame(obj2, manager.getById("id2"));
    }

    @Test
    void testObjectOfProxyManagerReRegister() {
        ObjectOfProxyManager manager = new ObjectOfProxyManager();
        Object obj = new Object();
        manager.set(obj, "id1");
        long before = manager.getReverseProxyMap().get("id1").getLastRegistered();
        manager.reRegister("id1");
        long after = manager.getReverseProxyMap().get("id1").getLastRegistered();
        assertTrue(after >= before);
    }

    @Test
    void testRemoteProxyManagerSetAndGet() {
        RemoteProxyManager manager = new RemoteProxyManager();
        Object proxy = new Object();
        Client client = new Client("test");
        manager.set("id1", proxy, client);
        assertSame(proxy, manager.get("id1"));
    }

    @Test
    void testRemoteProxyManagerNonExistent() {
        RemoteProxyManager manager = new RemoteProxyManager();
        assertNull(manager.get("nonExistent"));
    }

    @Test
    void testRemoteProxyManagerClientMapping() {
        RemoteProxyManager manager = new RemoteProxyManager();
        Object proxy = new Object();
        Client client = new Client("test");
        manager.set("id1", proxy, client);
        assertTrue(manager.getClientMap().containsKey(client));
        assertTrue(manager.getClientMap().get(client).contains("id1"));
    }

    @Test
    void testPreArgObj() {
        PreArgObj proxyObj = new PreArgObj(ArgObjType.PROXY, new HashMap<String, Object>());
        assertEquals(ArgObjType.PROXY, proxyObj.getType());
        assertNotNull(proxyObj.getData());

        PreArgObj dataObj = new PreArgObj(ArgObjType.DATA, 42);
        assertEquals(ArgObjType.DATA, dataObj.getType());
        assertEquals(42, dataObj.getData());
    }

    @Test
    void testClientHostId() {
        RpcFramework.reset();
        RpcFramework.setHostId("unitTestHost");
        Client client = new Client();
        assertEquals("unitTestHost", client.getHostId());

        Client customClient = new Client("customHost");
        assertEquals("customHost", customClient.getHostId());
    }

    @Test
    void testMessageReceiverWaitingCount() {
        RpcFramework.reset();
        RpcFramework.setHostId("waitingCountTest");
        MessageReceiver receiver = new MessageReceiver("waitingCountTest");
        assertEquals(0, receiver.currentWaitingCount());
    }

    public static class HelloService {
        public String hello() {
            return "world";
        }
    }

    @Test
    void testMessageReceiverSetMain() {
        RpcFramework.reset();
        RpcFramework.setHostId("setMainTest");
        MessageReceiver receiver = new MessageReceiver("setMainTest");
        receiver.setMain(new HelloService());
        Object mainRetrieved = receiver.getProxyManager().getById("main");
        assertNotNull(mainRetrieved);
    }

    @Test
    void testDeleteProxyById() {
        RpcFramework.reset();
        RpcFramework.setHostId("delProxyByIdTest");
        MessageReceiver receiver = new MessageReceiver("delProxyByIdTest");
        Object obj = new Object();
        receiver.getProxyManager().set(obj, "delId1");
        assertSame(obj, receiver.getProxyManager().getById("delId1"));
        RpcFramework.deleteProxyById("delId1", "delProxyByIdTest");
        assertNull(receiver.getProxyManager().getById("delId1"));
    }

    @Test
    void testDeleteProxy() {
        RpcFramework.reset();
        RpcFramework.setHostId("delProxyTest");
        MessageReceiver receiver = new MessageReceiver("delProxyTest");
        Object obj = new Object();
        receiver.getProxyManager().set(obj, "delId2");
        assertTrue(receiver.getProxyManager().has(obj));
        RpcFramework.deleteProxy(obj, "delProxyTest");
        assertFalse(receiver.getProxyManager().has(obj));
    }

    @Test
    void testIdentityBasedProxy() {
        // 验证ObjectOfProxyManager使用引用（而非equals）识别对象
        ObjectOfProxyManager manager = new ObjectOfProxyManager();
        Object obj1 = new Object();
        Object obj2 = new Object();
        manager.set(obj1, "id1");
        manager.set(obj2, "id2");
        assertEquals("id1", manager.get(obj1));
        assertEquals("id2", manager.get(obj2));
        // IdentityHashMap按引用识别，不同对象即使内容相同也有不同id
        assertSame(obj1, manager.getById("id1"));
        assertSame(obj2, manager.getById("id2"));
        assertNotSame(obj1, obj2);
    }
}
