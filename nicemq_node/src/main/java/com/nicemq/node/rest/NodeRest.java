package com.nicemq.node.rest;

import java.util.Map;
import java.util.Set;

import org.axe.annotation.ioc.Controller;
import org.axe.annotation.mvc.Request;
import org.axe.annotation.mvc.RequestParam;
import org.axe.constant.RequestMethod;

import com.nicemq.common.constant.ClientMatchMode;
import com.nicemq.node.core.TcpClient;
import com.nicemq.node.core.TcpClientManager;
import com.tunnel.common.constant.Constant;
import com.tunnel.common.util.CollectionUtil;

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
	public String send_msg(
		@RequestParam(name="tags",required=true,desc="路径，多层次使用Constant.SPLIT_FLAG分割")String tags,
		@RequestParam(name="matchMode",required=false,desc="多层次路径匹配方式，FULL：唯一全匹配，MORE：广播")String matchMode,
		@RequestParam(name="msg",required=true,desc="消息")String message
			){
		ClientMatchMode mode = ClientMatchMode.getMode(matchMode);
		if(mode == null){
			return "不支持的路径匹配："+matchMode;
		}
		Set<TcpClient> clientSet = TcpClientManager.get(tags.split(Constant.SPLIT_FLAG), mode);
		if(CollectionUtil.isEmpty(clientSet)){
//			LogUtil.log("Client 不存在");
			return "Client 不存在";
		}
		for(TcpClient client:clientSet){
			client.sendMsg(message);
		}
		
		return "SUCCESS";
	}
	
}