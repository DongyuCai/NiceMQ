package com.nicemq.common.constant;

import java.util.HashMap;
import java.util.Map;

/**
 * 消费者路径匹配方式
 */
public enum ClientMatchMode {
	//全匹配，不多也不少
	FULL_MATCH("FULL"),
	//多匹配，全匹配+当前层的所有下层、下下层节点
	MORE_MATCH("MORE");
	
	private static Map<String,ClientMatchMode> MODE_MAP = new HashMap<>();
	static {
		for(ClientMatchMode mode:values()){
			MODE_MAP.put(mode.name, mode);
		}
	}
	
	//*注意：
	//全匹配也可以做到一条消息，多个消费者，
	//只要消费者的tags路径都一样，那么他们会处在一个set中
	//而多匹配，不仅仅包含tags完全匹配得到的client 集合（集合可能空、1个元素、多个元素）
	//也包含此路径下，继续往后的路径以及client集合，适合广播消息
	
	private String name;

	public static ClientMatchMode getMode(String name){
		return MODE_MAP.get(name);
	}
	
	private ClientMatchMode(String name) {
		this.name = name;
	}
	
	public String getMode() {
		return name;
	}
}
