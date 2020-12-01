package test;

import org.axe.util.LogUtil;

import com.nicemq.client.core.MqClient;
import com.nicemq.client.core.MsgListener;

public class Test {
	public static void main(String[] args) {
		//测试
		MqClient.createConsumer("192.168.199.45", 6619, new String[]{"gudidai","box","A22","20201113145640932"}, new MsgListener() {
			@Override
			public void receive(String msg) {
				LogUtil.log("收到："+msg);
			}
		});
		System.out.println("ok");
	}
}
