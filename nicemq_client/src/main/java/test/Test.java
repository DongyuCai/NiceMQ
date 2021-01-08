package test;

import org.axe.util.LogUtil;

import com.nicemq.client.core.ConnectSuccessListener;
import com.nicemq.client.core.MqClient;
import com.nicemq.client.core.MsgListener;

public class Test {
	public static void main(String[] args) {
		//测试
		MqClient.createConsumer("218.90.120.43", 6619, new String[]{"gudidai","box","20201113145640931"}, new ConnectSuccessListener() {
			@Override
			public void connectSuccess() {
				LogUtil.log("连接成功");
			}
		},new MsgListener() {
			@Override
			public void receive(String msg) {
				LogUtil.log("收到："+msg);
			}
		});
	}
}
