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
package com.nicemq.client.core;

import org.axe.util.LogUtil;

import com.tunnel.common.constant.Constant;
import com.tunnel.common.nio.EventLoopGroupManager;
import com.tunnel.common.tunnel.TunnelBaseHandler;
import com.tunnel.common.tunnel.TunnelDataQueueManager;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

public class MqClientHandler extends TunnelBaseHandler {
	private MqClient client;
	private String[] tags;//路径
	private MsgListener msgListener;
	
	protected MqClientHandler(MqClient client,String[] tags,MsgListener msgListener) {
		super("mq client handler");
		this.client = client;
		this.tags = tags;
		this.msgListener = msgListener;
	}
	
	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		super.channelActive(ctx);
		//连接成功，设置全局对象
		ChannelHandlerContextManager.setChannelHandlerContext(ctx);
		
		//注册
		/*String localHost = "...";
		try {
			localHost = InetAddress.getLocalHost().getHostAddress();
		} catch (Exception e) {
			LogUtil.error(e);
		}*/
		String content = String.join(Constant.SPLIT_FLAG, this.tags);
		TunnelDataQueueManager.commitData(TunnelDataQueueManager.REGISTER_MSG, ctx, content.getBytes());
	}

	@Override
	protected void handleData(ChannelHandlerContext ctx, ByteBuf buf, byte flag) {
		
		switch (flag) {
			case TunnelDataQueueManager.PING_MSG:
//				LogUtil.log("收到心跳");
				break;
				
			//注册返回结果
			case TunnelDataQueueManager.REGISTER_MSG:
				doRegisterResult(ctx, buf);
				break;
			//TCP
			case TunnelDataQueueManager.TCP_DATA_MSG:
				tcpDataMsg(ctx,buf);
				break;
			default:
				break;
		}
	}

	public void doRegisterResult(ChannelHandlerContext ctx, ByteBuf buf){
		byte[] content = new byte[buf.readableBytes()];
		buf.getBytes(buf.readerIndex(), content,0,content.length);
		String result = new String(content);
//		LogUtil.log("mq node connect result:"+result);
		if(!result.contains("REGISTER SUCCESS")){
			//关闭netty
			EventLoopGroupManager.shutdown(0);
//			System.exit(0);//停止程序
		}
	}
	
	
	public void tcpDataMsg(ChannelHandlerContext ctx, ByteBuf buf){
		byte[] content = new byte[buf.readableBytes()];
		buf.getBytes(buf.readerIndex(), content,0,content.length);
		String result = new String(content);
		this.msgListener.receive(result);
		//前16个字符是
		/*if(buf.readableBytes() > 16){
			byte[] connectionIdBytes = new byte[16];
			buf.getBytes(buf.readerIndex(), connectionIdBytes, 0, 16);
			
			byte[] dataBytes = new byte[buf.readableBytes()-16];
			buf.getBytes(buf.readerIndex()+16, dataBytes, 0, dataBytes.length);
			
			//TODO
			LogUtil.log(new String(dataBytes));
		}*/
	}

	
    @Override  
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        LogUtil.error(cause); 
        ctx.close();
    }
	
	@Override
    protected void handleReaderIdle(ChannelHandlerContext ctx) {
        super.handleReaderIdle(ctx);
        ctx.close();
    }
	
	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		super.channelInactive(ctx);
		//断线重连
		this.client.connect();
	}

}