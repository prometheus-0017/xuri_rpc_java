package com.xuri.rpc;

import java.lang.reflect.Method;
import java.util.*;

/**
 * 参数转换器，对应TS版本的ArgTranslator。
 * 负责将Java对象序列化为可传输格式（toArgObj）和反序列化（reverseToArgObj）。
 *
 * 对象vs字典的区分规则：
 * - Map<String, Object> 被视为数据字典，直接序列化
 * - 其他非基本类型对象被视为"对象"，转为代理
 * - CallableObject 被视为可调用函数，生成带__call__的代理
 */
public class ArgTranslator {
    public static final String TYPE_INDICATOR = "__is_rpc_proxy__";

    /**
     * 将Java对象序列化为可传输格式。
     * @param target 要序列化的对象
     * @param asProxyFunc 将对象转为代理的回调
     * @return 序列化后的对象（null, 基本类型, Map, List）
     */
    public Object toArgObj(Object target, AsProxyFunction asProxyFunc) {
        if (target == null) {
            return null;
        }

        // PreArgObj处理
        if (target instanceof PreArgObj) {
            PreArgObj preArg = (PreArgObj) target;
            if (preArg.getType() == ArgObjType.PROXY) {
                return handlePreArgObj(preArg);
            } else if (preArg.getType() == ArgObjType.DATA) {
                return preArg.getData();
            } else {
                throw new RuntimeException("not implemented");
            }
        }

        // 基本类型直接返回
        if (isSimpleObject(target)) {
            return target;
        }

        // 数组/List - 递归处理
        if (target instanceof List) {
            List<?> list = (List<?>) target;
            List<Object> result = new ArrayList<Object>();
            for (Object item : list) {
                result.add(toArgObj(item, asProxyFunc));
            }
            return result;
        }

        // Java数组
        if (target.getClass().isArray()) {
            Object[] arr = (Object[]) target;
            List<Object> result = new ArrayList<Object>();
            for (Object item : arr) {
                result.add(toArgObj(item, asProxyFunc));
            }
            return result;
        }

        // Map - 被视为数据字典，递归处理每个值
        if (target instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) target;
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), toArgObj(entry.getValue(), asProxyFunc));
            }
            return result;
        }

        // 其他对象 - 检查是否是远程代理对象
        if (target instanceof RemoteProxyObject) {
            RemoteProxyObject rpo = (RemoteProxyObject) target;
            // 远程代理对象需要重新生成代理描述
            Map<String, Object> descMap = new LinkedHashMap<String, Object>();
            descMap.put("id", rpo.getProxyId());
            descMap.put("hostId", rpo.getClient().getHostId());
            descMap.put("members", new ArrayList<Object>());
            descMap.put(TYPE_INDICATOR, TYPE_INDICATOR);
            return descMap;
        }

        // 其他对象 - 转为代理
        PreArgObj preObj = asProxyFunc.asProxy(target);
        return handlePreArgObj(preObj);
    }

    /**
     * 将传输格式反序列化为Java对象。
     */
    public Object reverseToArgObj(Object target, Client client) {
        if (target == null) {
            return null;
        }

        // 检查是否是代理描述符
        if (target instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) target;
            if (map.containsKey(TYPE_INDICATOR)) {
                // 这是一个代理描述符
                ProxyDescriber describer = mapToProxyDescriber(map);
                Object result = client.createRemoteProxy(describer);
                client.getRunnableProxyManager().set(describer.getId(), result, client);
                return result;
            }

            // 普通字典，递归处理每个值
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (TYPE_INDICATOR.equals(key)) continue;
                result.put(key, reverseToArgObj(entry.getValue(), client));
            }
            return result;
        }

        // List - 递归处理
        if (target instanceof List) {
            List<?> list = (List<?>) target;
            List<Object> result = new ArrayList<Object>();
            for (Object item : list) {
                result.add(reverseToArgObj(item, client));
            }
            return result;
        }

        // 基本类型直接返回
        return target;
    }

    private Object handlePreArgObj(PreArgObj obj) {
        if (obj.getData() instanceof Map) {
            // 拷贝Map以避免修改原始数据
            Map<String, Object> dataCopy = new LinkedHashMap<String, Object>();
            Map<?, ?> original = (Map<?, ?>) obj.getData();
            for (Map.Entry<?, ?> entry : original.entrySet()) {
                dataCopy.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            dataCopy.put(TYPE_INDICATOR, TYPE_INDICATOR);
            return dataCopy;
        }
        // 对于非Map类型的代理数据，包装为Map
        Map<String, Object> wrapper = new LinkedHashMap<String, Object>();
        wrapper.put(TYPE_INDICATOR, TYPE_INDICATOR);
        if (obj.getData() != null) {
            wrapper.put("data", obj.getData());
        }
        return wrapper;
    }

    private boolean isSimpleObject(Object obj) {
        return obj instanceof String
                || obj instanceof Number
                || obj instanceof Boolean
                || obj instanceof byte[];
    }

    @SuppressWarnings("unchecked")
    private ProxyDescriber mapToProxyDescriber(Map<?, ?> map) {
        String id = (String) map.get("id");
        String hostIdVal = (String) map.get("hostId");
        List<MemberInfo> members = new ArrayList<MemberInfo>();

        Object membersObj = map.get("members");
        if (membersObj instanceof List) {
            for (Object m : (List<?>) membersObj) {
                if (m instanceof Map) {
                    Map<?, ?> mMap = (Map<?, ?>) m;
                    String type = (String) mMap.get("type");
                    String name = (String) mMap.get("name");
                    members.add(new MemberInfo(type, name));
                }
            }
        }

        return new ProxyDescriber(id, hostIdVal, members);
    }

    /**
     * 将对象转为代理的函数接口。
     */
    public interface AsProxyFunction {
        PreArgObj asProxy(Object obj);
    }
}
