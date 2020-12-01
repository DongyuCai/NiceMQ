package com.nicemq.node;

import java.util.Scanner;

import org.axe.util.LogUtil;
import org.axe.util.StringUtil;

import com.ajida.AxeAppConfig;
import com.ajida.SSHConfig;
import com.nicemq.common.util.Ajida4NiceMq;

/**
 * 后端代码更新到测试与生产环境
 */
public class UpdateCodeScript {

	public static void main(String[] args) {
//		fullProjectUpdate("192.168.199.45", 22, "test", "ybsl1234", "/usr/local","nicemq_node");
		fullProjectUpdate("218.90.120.43", 22, "pro","18962211182!189.cn", "/usr/local","nicemq_node");
	}
	
	public static void fullProjectUpdate(String ip,int port,String env,String password,String distDir,String appName){
		try {
			System.out.println("connect to "+ip);
			if(StringUtil.isEmpty(password)){
				System.out.println(">>please enter password:");
				Scanner sc = new Scanner(System.in);
				password = sc.nextLine();
				sc.close();
			}
			
			//ssh连接配置
			SSHConfig sshConfig = new SSHConfig(ip, "root", password,port);
			
			AxeAppConfig appConfig = new AxeAppConfig("com.nicemq.node.JettyStart 16619");
			
			appConfig.addConfigParams("env", env);
			
			Ajida4NiceMq.axeFullProjectUpdate(false,env, appConfig, new String[]{}, sshConfig, distDir,new String[]{});
		} catch (Exception e) {
			LogUtil.error(e);
		}
	}
	
}
