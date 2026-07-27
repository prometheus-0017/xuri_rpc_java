package com.xuri.rpc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 代理描述符，对应TS版本的ProxyDescriber。
 * 包含代理对象的id、hostId和成员列表。
 */
public class ProxyDescriber {
    private String id;
    private String hostId;
    private List<MemberInfo> members;

    public ProxyDescriber(String id, String hostId, List<MemberInfo> members) {
        this.id = id;
        this.hostId = hostId;
        this.members = members != null ? members : new ArrayList<MemberInfo>();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getHostId() { return hostId; }
    public void setHostId(String hostId) { this.hostId = hostId; }
    public List<MemberInfo> getMembers() { return members; }
    public void setMembers(List<MemberInfo> members) { this.members = members; }

    /**
     * 转换为可序列化的Map格式（用于网络传输）。
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("id", id);
        map.put("hostId", hostId);
        List<Map<String, String>> memberList = new ArrayList<Map<String, String>>();
        for (MemberInfo m : members) {
            Map<String, String> mMap = new LinkedHashMap<String, String>();
            mMap.put("type", m.getType());
            mMap.put("name", m.getName());
            memberList.add(mMap);
        }
        map.put("members", memberList);
        return map;
    }
}
