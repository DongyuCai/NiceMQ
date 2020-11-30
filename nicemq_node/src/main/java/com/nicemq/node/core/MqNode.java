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

import java.util.concurrent.TimeUnit;

import org.axe.interface_.mvc.Listener;
import org.axe.util.LogUtil;

import com.nicemq.node.constant.SysConfig;
import com.tunnel.common.constant.Constant;
import com.tunnel.common.nio.EventLoopGroupManager;
import com.tunnel.common.tunnel.TunnelDataQueueManager;
import com.tunnel.common.util.PortUtil;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.timeout.IdleStateHandler;  
  
public class MqNode extends Thread implements Listener{  
	
    public MqNode() throws Exception {  
        this.setName("mq node thread");

        boolean portIsUsing = PortUtil.checkPortIsUsing(SysConfig.mq_port());
        if(portIsUsing){
        	throw new Exception("port "+SysConfig.mq_port()+" is using");
        }
    }  
    
    @Override
    public void run(){  
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);  
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        EventLoopGroupManager.add(SysConfig.mq_port(), bossGroup,workerGroup);
        try {  
            ServerBootstrap sbs = new ServerBootstrap()
            		.group(bossGroup,workerGroup)
            		.channel(NioServerSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)  
                    .childHandler(new ChannelInitializer<SocketChannel>() {  
                        
                        protected void initChannel(SocketChannel ch) throws Exception {
                        	ChannelPipeline p = ch.pipeline();
                        	//10秒钟心跳检测，客户端5秒发一次心跳，如果10秒没收到，断开连接
                        	p.addLast(new IdleStateHandler(10, 0, 0,TimeUnit.SECONDS));
                        	//TODO：此处是限制了server向client传递的数据大小，不超过20M
                        	p.addLast(new DelimiterBasedFrameDecoder(1024*1024*20,Unpooled.copiedBuffer(Constant.TUNNEL_DATA_END_FLAG_BYTES)));
                        	p.addLast(new MqNodeHandler());  
                        };  
                    }).option(ChannelOption.SO_BACKLOG, 128)//接受连接的队列长度，超过128个连接请求，就会拒绝后续连接
                    .option(ChannelOption.SO_REUSEADDR,true);// Socket参数，地址复用
             // 绑定端口，开始接收进来的连接  
             ChannelFuture future = sbs.bind(SysConfig.mq_port()).sync();    

             LogUtil.log("mq node listen at " + SysConfig.mq_port() );
             future.channel().closeFuture().sync();  
        } catch (Exception e) {  
        	EventLoopGroupManager.shutdown(SysConfig.mq_port());
        }  
    }

	@Override
	public int index() {
		return 0;
	}

	@Override
	public void init() throws Exception {
		
		//启动消息队列
		TunnelDataQueueManager.startTunnelDataQueue("mq node", true);
		
		this.start();//开始启动线程...
	}	
}  