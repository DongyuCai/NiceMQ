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
  
import java.util.concurrent.TimeUnit;

import org.axe.util.LogUtil;

import com.tunnel.common.constant.Constant;
import com.tunnel.common.nio.EventLoopGroupManager;
import com.tunnel.common.tunnel.TunnelDataQueueManager;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.timeout.IdleStateHandler;  
/**
 * S->C通讯管道
 * 初始连接时候，负责客户端注册
 * 注册成功后，负责启动汇报管道
 * 全部启动完成后，开始接受请求
 */
public class MqClient{
	private EventLoopGroup group = new NioEventLoopGroup();  
	private Bootstrap  bootstrap;
    private Channel channel;
    private int connectFailedTimes = 0;//掉线后重连次数
    private volatile int connectNextTimeSec = 0;//掉线后下次重连等待时间，单位秒
    //mq节点的地址
    private String mqNodeIp = null;
    //mq节点的端口
    private int mqNodePort = 0;
    
    public MqClient(String mqNodeIp,int mqNodePort) {
    	//启动消息队列，不怕重复启动
    	TunnelDataQueueManager.startTunnelDataQueue("mq client", true);
    	
    	this.mqNodeIp = mqNodeIp;
    	this.mqNodePort = mqNodePort;
	}
    
    //bootstrap配置好
    public void start(String[] tags,MsgListener msgListener){
    	EventLoopGroupManager.add(0, group);
    	bootstrap = new Bootstrap();
    	bootstrap.group(group)
         .channel(NioSocketChannel.class)
//             .option(ChannelOption.TCP_NODELAY, true)
         .handler(new ChannelInitializer<SocketChannel>() {
             @Override
             public void initChannel(SocketChannel ch) throws Exception {
                 ChannelPipeline p = ch.pipeline();
                 //5秒内没有数据发送，就需要发送一个心跳包维持服务端的连接
                 //由于此管道，除了在连接之初会汇报注册信息，剩余时间都没有数据上传，因此会每5秒一次心跳
             	 //10秒钟心跳检测，客户端5秒发一次心跳，如果10秒没收到，断开连接
             	 p.addLast(new IdleStateHandler(10, 0, 0,TimeUnit.SECONDS));
                 //请求内容不超过10M
                 p.addLast(new DelimiterBasedFrameDecoder(1024*1024*10,Unpooled.copiedBuffer(Constant.TUNNEL_DATA_END_FLAG_BYTES)));
                 p.addLast(new MqClientHandler(MqClient.this,tags,msgListener));
             }
         });
    	connect();
    }
    
    
    public void connect(){
        if (channel != null && channel.isActive()) {
            return;
        }

        ChannelFuture future = bootstrap.connect(this.mqNodeIp, this.mqNodePort);  
        future.addListener(new ChannelFutureListener() {
            public void operationComplete(ChannelFuture futureListener) throws Exception {
                if (futureListener.isSuccess()) {
                    channel = futureListener.channel();
                    connectFailedTimes = 0;//清空失败的连接次数
                    connectNextTimeSec = 0;//清空失败后重连等待时间
                    LogUtil.log("mq node connect success");
                } else {
                	connectFailedTimes++;
                	if(connectNextTimeSec<10){
                		connectNextTimeSec++;//小于10秒的情况下，没次失败就多等1秒
                	}
                	LogUtil.log("failed to connect mq node x"+connectFailedTimes+", try connect after "+connectNextTimeSec+"s");
                   /* if(connectFailedTimes > 3){
                    	//超过三次，放弃连接，全部关闭
                    	LogUtil.log("tunnel client shutdown");
                    	group.shutdownGracefully();
                    }else{*/
                    	futureListener.channel().eventLoop().schedule(new Runnable() {
                            @Override
                            public void run() {
                            	connect();
                            }
                        }, connectNextTimeSec, TimeUnit.SECONDS);
//                    }
                }
            }
        });
    }
    
    public static void main(String[] args) {

		//测试
    	new MqClient("192.168.199.45", 6619)
    	.start(new String[]{"gudidai","box","A22","20201113145640932"}, new MsgListener() {
			@Override
			public void receive(String msg) {
				LogUtil.log("处理:"+msg);
			}
		});
	}
}