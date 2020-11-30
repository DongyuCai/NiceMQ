package com.nicemq.client.core;

/**
 * 消息接收处理回调
 */
public interface MsgListener {

	public void receive(String msg);
	
}
