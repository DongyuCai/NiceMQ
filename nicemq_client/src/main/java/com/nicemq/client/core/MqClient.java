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
  
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.axe.util.HttpUtil;
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
public final class MqClient{
	
	/**
	 * 创建一个消息消费者
	 * @param mqNodeIp 消息服务节点的ip
	 * @param mqNodePort 消息服务节点的端口
	 * @param tags 路径层次
	 * @param msgListener 回调处理
	 * @return
	 */
	public static MqClient createConsumer(String mqNodeIp,int mqNodePort,String[] tags,MsgListener msgListener){
		MqClient client = new MqClient(mqNodeIp, mqNodePort);
		client.startListenMsg(tags, msgListener);
		return client;
	}
	
	/**
	 * 创建一个消息生产者
	 * @param hostUri mq服务http地址（http://ip:16619），可以是域名
	 * @return
	 */
	public static MqClient createProvider(String hostUri){
		return new MqClient(hostUri);
	}
	
	private EventLoopGroup group = new NioEventLoopGroup();  
	private Bootstrap  bootstrap;
    private Channel channel;
    private int connectFailedTimes = 0;//掉线后重连次数
    private volatile int connectNextTimeSec = 0;//掉线后下次重连等待时间，单位秒
    //mq节点的地址
    private String mqNodeIp = null;
    //mq节点的端口
    private int mqNodePort = 0;
    
    private MqClient(String mqNodeIp,int mqNodePort) {
    	//启动消息队列，不怕重复启动
    	TunnelDataQueueManager.startTunnelDataQueue("mq client", true);
    	
    	this.mqNodeIp = mqNodeIp;
    	this.mqNodePort = mqNodePort;
	}
    
    private String hostSendMsgUrl;//mq服务的发送消息地址
    private MqClient(String hostUri){
    	if(!hostUri.endsWith("/")){
    		hostUri = hostUri+"/";
    	}
    	hostSendMsgUrl = hostUri+"node/send_msg";
    }
    
    //bootstrap配置好
    private void startListenMsg(String[] tags,MsgListener msgListener){
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
    
    
    void connect(){
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
    
    //发送设备指令
  	public void sendMsg(String[] tags,String msg)throws Exception{
  		//防止msg破坏http发送的json格式，需要msg里转意
  		Map<String,String> params = new HashMap<>();
  		params.put("tags", String.join("#_SPLIT-910810#", tags));
  		params.put("matchMode", "FULL");
  		params.put("msg", msg);
  		HttpUtil.sendPost(hostSendMsgUrl, params);
  	}
}