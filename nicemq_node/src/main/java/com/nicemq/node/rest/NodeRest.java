package com.nicemq.node.rest;

import java.util.Map;
import java.util.Set;

import org.axe.annotation.ioc.Controller;
import org.axe.annotation.mvc.Request;
import org.axe.annotation.mvc.RequestParam;
import org.axe.constant.RequestMethod;
import org.axe.exception.RestException;

import com.nicemq.common.constant.ClientMatchMode;
import com.nicemq.node.core.TcpClientManager;
import com.nicemq.node.core.TcpClientManager.TcpClient;
import com.tunnel.common.constant.Constant;
import com.tunnel.common.tunnel.TunnelDataQueueManager;
import com.tunnel.common.util.CollectionUtil;
import com.tunnel.common.util.LogUtil;

@Controller(basePath="/node",desc="对外接口")
public class NodeRest {
	
	@Request(path="/client_tree",method=RequestMethod.GET,desc="查看连接树")
	public String client_tree(){
		return TcpClientManager.getClientTagsTree();
	}
	
	@Request(path="/all_client_tags",method=RequestMethod.GET,desc="查看所有连接客户端的tags")
	public Map<String,Integer> all_client_tags(){
		return TcpClientManager.getAllClientTags();
	}
	
	@Request(path="/send_msg",method=RequestMethod.POST,desc="发送消息")
	public void send_msg(
		@RequestParam(name="tags",required=true,desc="路径，多层次使用Constant.SPLIT_FLAG分割")String tags,
		@RequestParam(name="matchMode",required=false,desc="多层次路径匹配方式，必须使用ClientMatchMode里指定方式")String matchMode,
		@RequestParam(name="msg",required=true,desc="消息")String message
			){
		ClientMatchMode mode = ClientMatchMode.getMode(matchMode);
		if(mode == null){
			LogUtil.log("不支持的路径匹配："+matchMode);
			throw new RestException("不支持的路径匹配："+matchMode);
		}
		Set<TcpClient> clientSet = TcpClientManager.get(tags.split(Constant.SPLIT_FLAG), mode);
		if(CollectionUtil.isEmpty(clientSet)){
			LogUtil.log("Client 不存在");
			throw new RestException("Client 不存在");
		}
		for(TcpClient client:clientSet){
			TunnelDataQueueManager.commitData(TunnelDataQueueManager.TCP_DATA_MSG, client.getCtx(), message.getBytes());
		}
	}
	
}