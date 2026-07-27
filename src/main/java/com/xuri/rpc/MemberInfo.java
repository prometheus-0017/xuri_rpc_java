package com.xuri.rpc;

/**
 * 代理成员描述，对应TS版本的ProxyDescriber中的members元素。
 */
public class MemberInfo {
    private String type; // "function" or "property"
    private String name;

    public MemberInfo(String type, String name) {
        this.type = type;
        this.name = name;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
