package com.nicemq.node.core;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.tunnel.common.constant.Constant;
import com.tunnel.common.tunnel.TunnelDataQueueManager;

import io.netty.channel.ChannelHandlerContext;

/**
 * 客户的通讯工具 客户端
 */
public class TcpClient {

	// 名称，由客户端连接时上传
	private String name;

	/**
	 * 客户的标签
	 */
	private String tags;
	private String[] tagsAry;

	/**
	 * 最后的10个数据发送记录
	 */
	private List<Map<String, String>> lastTenMessage = new LinkedList<>();

	private ChannelHandlerContext ctx;

	private String createTime;

	public TcpClient(String tags, ChannelHandlerContext ctx) {
		// tags格式：name+两遍分割符+路由标签1+分割+路由标签2...
		// name+SPLIT_FLAG+SPLIT_FLAG+tag1+SPLIT_FLAG+tag2...
		String[] split = tags.split(Constant.SPLIT_FLAG + Constant.SPLIT_FLAG);
		if (split.length > 1) {
			this.name = split[0];
			tags = split[1];
		} else {
			this.name = "unknown";
		}

		this.tags = tags;
		this.tagsAry = tags.split(Constant.SPLIT_FLAG);
		this.ctx = ctx;

		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		this.createTime = sdf.format(new Date());
	}

	public void sendMsg(String message) {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		TunnelDataQueueManager.commitData(TunnelDataQueueManager.TCP_DATA_MSG, this.ctx, message.getBytes());
		synchronized (lastTenMessage) {
			Map<String,String> map = new HashMap<>();
			map.put("time", sdf.format(new Date()));
			map.put("message", message);
			lastTenMessage.add(map);
			if(lastTenMessage.size()>10){
				lastTenMessage.remove(0);
			}
		}
	}

	public String getName() {
		return name;
	}

	public String getTags() {
		return tags;
	}

	public String[] getTagsAry() {
		return tagsAry;
	}
	
	public List<Map<String, String>> getLastTenMessage() {
		return lastTenMessage;
	}

	public String getCreateTime() {
		return createTime;
	}

}