/**
 * MIT License
 * 
 * Copyright (c) 2017 CaiDongyu
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.nicemq.node.core;
  
import org.axe.util.LogUtil;
import org.axe.util.StringUtil;

import com.nicemq.node.core.TcpClientManager.TcpClient;
import com.tunnel.common.constant.Constant;
import com.tunnel.common.tunnel.TunnelBaseHandler;
import com.tunnel.common.tunnel.TunnelDataQueueManager;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;  
  
public class MqNodeHandler extends TunnelBaseHandler{
	public MqNodeHandler() {
		super("mq node handler");
	}

	public static final AttributeKey<TcpClient> CTX_ATTR_TCP_CLIENT_KEY = AttributeKey.valueOf("TcpClient"); 
    
	@Override
	protected void handleData(ChannelHandlerContext ctx, byte[] data, byte flag) {
		switch (flag) {
			case TunnelDataQueueManager.PING_MSG:
//				LogUtil.log("Tunnel心跳");
				break;
			//注册
			case TunnelDataQueueManager.REGISTER_MSG:
				registerMsg(ctx,data);
				break;
			default:
				break;
		}
	}
	

    private void registerMsg(ChannelHandlerContext ctx, byte[] data) {
    	//客户端来的注册信息
        String content = new String(data);
		if (StringUtil.isNotEmpty(content) || content.contains(Constant.SPLIT_FLAG)) {
			//content格式：
			//tag1+SPLIT_FLAG+tag2...
			
			TcpClient client = TcpClientManager.add(new TcpClient(content,ctx));
			
			//如果登记注册成功
    		//就把这个ctx里，记上对应的client，因为在ctx异常或者关闭时候，client是要被销毁的
    		Attribute<TcpClient> attr = ctx.attr(CTX_ATTR_TCP_CLIENT_KEY);
    		attr.setIfAbsent(client);
			
			//返回信息
			TunnelDataQueueManager.commitData(TunnelDataQueueManager.REGISTER_MSG, ctx, "REGISTER SUCCESS".getBytes());
			
		}
	}
    
    @Override  
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		LogUtil.error(cause);
        ctx.close();
    }
	
	@Override
    protected void handleReaderIdle(ChannelHandlerContext ctx) {
//        LogUtil.log("timeout");
        super.handleReaderIdle(ctx);
        ctx.close();
    }
    
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    	super.channelInactive(ctx);
    	try {
	        LogUtil.log("remove tcp client");
    		Attribute<TcpClient> attr = ctx.attr(CTX_ATTR_TCP_CLIENT_KEY);
        	TcpClient client = attr.get();
            if(client != null){
        		TcpClientManager.remove(client);
            }
		} catch (Exception e) {
			LogUtil.error(e);
		}
    }
}  