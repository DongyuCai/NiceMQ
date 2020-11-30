package com.nicemq.node.constant;

import java.util.Properties;

import org.axe.util.PropsUtil;

public final class SysConfig {
	
	
	private static final Properties properties = PropsUtil.loadProps("sys.properties");

	public static int http_port(){
		return PropsUtil.getInt(properties, "http_port");
	}

	public static int mq_port(){
		return PropsUtil.getInt(properties, "mq_port");
	}
}
