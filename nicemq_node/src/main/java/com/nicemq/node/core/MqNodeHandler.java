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
  
import java.io.UnsupportedEncodingException;

import org.axe.util.LogUtil;
import org.axe.util.StringUtil;

import com.nicemq.node.core.TcpClientManager.TcpClient;
import com.tunnel.common.constant.Constant;
import com.tunnel.common.tunnel.TunnelBaseHandler;
import com.tunnel.common.tunnel.TunnelDataQueueManager;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;  
  
public class MqNodeHandler extends TunnelBaseHandler{
	public MqNodeHandler() {
		super("mq node handler");
	}

	public static final AttributeKey<TcpClient> CTX_ATTR_TCP_CLIENT_KEY = AttributeKey.valueOf("TcpClient"); 
    

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		super.channelActive(ctx);
	}

	@Override
	protected void handleData(ChannelHandlerContext ctx, ByteBuf buf, byte flag) {
		switch (flag) {
			case TunnelDataQueueManager.PING_MSG:
//				LogUtil.log("Tunnel心跳");
				break;
			//注册
			case TunnelDataQueueManager.REGISTER_MSG:
				registerMsg(ctx,buf);
				break;
			//TCP
			case TunnelDataQueueManager.TCP_DATA_MSG:
				tcpDataMsg(ctx, buf);
				break;
			default:
				break;
		}
	}
	

    private void registerMsg(ChannelHandlerContext ctx, ByteBuf buf) {
    	//客户端来的注册信息
		byte[] data = new byte[buf.readableBytes()];
    	buf.getBytes(buf.readerIndex(), data,0,data.length);
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
    
	private void tcpDataMsg(ChannelHandlerContext ctx, ByteBuf buf){
		//前16个字符是
		//connectionId 时间戳+三位随机数，代表http请求的编号 占16位
		if(buf.readableBytes() > 16){
			byte[] connectionIdBytes = new byte[16];
			buf.getBytes(buf.readerIndex(), connectionIdBytes, 0, 16);
			
			byte[] dataBytes = new byte[buf.readableBytes()-16];
			if(dataBytes.length > 0){
				buf.getBytes(buf.readerIndex()+16, dataBytes, 0, dataBytes.length);
			}
			try {
				LogUtil.log(new String(dataBytes,"UTF-8"));
			} catch (UnsupportedEncodingException e) {
				LogUtil.error(e);
			}
			/*TODO 改成根据路由，消息发送到其他client
			TcpChannelContext tcpCtx = TcpChannelManager.get(new String(connectionIdBytes));
			if(tcpCtx != null){
				//这里不同于httpServerHandler
				//TcpServer没有Http解析器，他只识别管道中的ByteBuf数据，byte[]数据是无法发送出去的
//				LogUtil.log(new String(dataBytes));
//				ByteBuf buffer = ctx.alloc().buffer();
//		        buffer.writeBytes(dataBytes);

                //netty channel里发送数据是都堆在堆内存里的，空间限制，因此如果写的太快，能回oom
                int waiteMs = 1;
                while(!ctx.channel().isWritable()){
                	try {
                		Thread.sleep(waiteMs);
					} catch (Exception e) {}
                	waiteMs = waiteMs+1;
                }
                
				tcpCtx.getChannel().writeAndFlush(dataBytes);
			}*/
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