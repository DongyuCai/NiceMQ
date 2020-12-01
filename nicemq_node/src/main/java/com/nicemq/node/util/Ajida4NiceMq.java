package com.nicemq.node.util;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import org.axe.util.LogUtil;
import org.axe.util.StringUtil;

import com.ajida.Ajida;
import com.ajida.AxeAppConfig;
import com.ajida.SSHConfig;
import com.ajida.SSHUtil;
import com.ajida.ZipUtil;

import ch.ethz.ssh2.Connection;

/**
 * 与ajida比 少了nginx那部分内容，并且不需要热部署
 * CaiDongyu 2020/3/10
 */
public final class Ajida4NiceMq extends Ajida{
	
	//build工程
	public static void axeBuildProject(boolean needGitPull, String env,AxeAppConfig appConfig, String[] sdkProjectNameAry) throws Exception{
		String path = new File("").getAbsolutePath();
		String rootPath = path.substring(0, path.lastIndexOf("\\"));
		String projectName = path.substring(path.lastIndexOf("\\") + 1);
		
		// build工程
		if (needGitPull) {
			gitPull(rootPath);
		}
	
		// mvn安装sdk工程
		for (String sdkProjectName : sdkProjectNameAry) {
			mvnInstallJar(rootPath + "\\" + sdkProjectName);
		}
	
		// mvn打包工程
		mvnPackageJarApplication(rootPath + "\\" + projectName, rootPath + "\\" + projectName + "\\config\\" + env,appConfig);
	}
	
	//压缩buld文件夹
	public static String axeCompressBuild(String[] includeRegs, String[] excludeRegs) throws Exception{
		// 压缩工程
		String path = new File("").getAbsolutePath();
		String rootPath = path.substring(0, path.lastIndexOf("\\"));
		String projectName = path.substring(path.lastIndexOf("\\") + 1);
		String zipName = projectName;
		
		String projectPath = rootPath + "\\" + projectName;
		LogUtil.log(">>> compress project to zip");
		ZipUtil.zip(new File(projectPath + "\\target\\" + projectName),projectPath + "\\target\\" + zipName + ".zip",includeRegs,excludeRegs);
		return zipName;
	}
	
	/**
	 * zip包更新
	 * @throws Exception 
	 */
	public static void axeUploadZip(SSHConfig sshConfig,String zipName, String remoteProjectDir, boolean cleanUpload) throws Exception{
		String path = new File("").getAbsolutePath();
		String rootPath = path.substring(0, path.lastIndexOf("\\"));
		String projectName = path.substring(path.lastIndexOf("\\") + 1);
		
		Connection sshConnection = null;
		try{
			
			int timeout = 10;
			// 获取链接
			sshConnection = SSHUtil.connect(sshConfig);
			if (sshConnection == null) {
				throw new Exception("连接失败");
			}
			// 上传到服务器
			sshFileUpload(sshConnection, rootPath + "\\" + projectName + "\\target\\" + zipName + ".zip",
					remoteProjectDir);

			// 停掉老的app
			String stopZipName = projectName;
			String pid = SSHUtil.getPid(remoteProjectDir + "/" + stopZipName + " | grep java", timeout, sshConnection);
			while (StringUtil.isNotEmpty(pid)) {
				SSHUtil.exec(sshConnection, "kill -9 " + pid, timeout, false);
				pid = SSHUtil.getPid(remoteProjectDir + "/" + stopZipName + " | grep java", timeout, sshConnection);
			}
			LogUtil.log(">>> 已停止 " + stopZipName);
			
			// 删除远程文件夹
			if(cleanUpload){
				try {
					SSHUtil.exec(sshConnection, "rm -rf " + remoteProjectDir + "/" + zipName, timeout, false);
					LogUtil.log(">>> 删除目录 " + remoteProjectDir + "/" + zipName);
				} catch (Exception e) {
					LogUtil.error(e);
				}
			}

			// 解压新包
			unzipRemotFile(sshConnection, timeout, remoteProjectDir + "/" + zipName + ".zip",
					remoteProjectDir + "/" + zipName);

			// 启动app
			//由于buster系统使用deb包管理，只有fromdos命令，没有像Centos里的dos2unix，所以这里要改
			SSHUtil.exec(sshConnection, new String[] { "cd " + remoteProjectDir + "/" + zipName, "chmod 777 -R *",
					"dos2unix start.sh", "echo '' > log.txt", "./start.sh" }, timeout, false);
			LogUtil.log("正在启动 " + zipName);

			// 等待启动成功
			Set<String> tailSet = new HashSet<>();// 排除tail到的重复行内容
			while (true) {
				Thread.sleep(1000);
				try {
					String cat = SSHUtil.exec(sshConnection,
							"tail -n10 " + remoteProjectDir + "/" + zipName + "/log.txt", timeout, true);
					String[] splitRows = cat.split("\r\n");
					for (String row : splitRows) {
						if (!tailSet.contains(row)) {
							LogUtil.log(row);
						}
					}
					tailSet.clear();
					for (String row : splitRows) {
						tailSet.add(row);
					}
					if (cat.contains("Axe started success!")) {
						LogUtil.log(">>> " + zipName + "启动成功");
						break;
					}
				} catch (Exception e) {}
			}
			
		} catch (Exception e) {
			throw e;
		} finally {
			if(sshConnection != null){
				try {
					sshConnection.close();
				} catch (Exception e2) {
				}
			}
		}
	}
	
	//全量更新
	public static void axeFullProjectUpdate(boolean needGitPull, String env, AxeAppConfig appConfig, String[] sdkProjectNameAry, SSHConfig sshConfig, String remoteProjectDir,String[] excludeRegs)
			throws Exception {
		//只是不排除任何文件的全量更新，并且cleanUpload=true，表明删除原本文件夹
		axeProjectUpdate(needGitPull, env, appConfig, sdkProjectNameAry, sshConfig, remoteProjectDir, null, excludeRegs,true);
	}

	//小量更新
	public static void axeSmallProjectUpdate(boolean needGitPull, String env, AxeAppConfig appConfig, String[] sdkProjectNameAry, SSHConfig sshConfig, String remoteProjectDir,String[] includeRegs)
			throws Exception {
		axeProjectUpdate(needGitPull, env, appConfig, sdkProjectNameAry, sshConfig, remoteProjectDir, includeRegs, null,false);
	}
	
	//少更新
	public static void axeProjectUpdate(boolean needGitPull, String env, AxeAppConfig appConfig, String[] sdkProjectNameAry, SSHConfig sshConfig, String remoteProjectDir,String[] includeRegs,String[] excludeRegs,boolean cleanUpload)
			throws Exception {
		// build工程
		axeBuildProject(needGitPull, env, appConfig, sdkProjectNameAry);
	
		// 压缩工程
		String zipName = axeCompressBuild(includeRegs, excludeRegs);
		
		//全量更新工程
		axeUploadZip(sshConfig, zipName, remoteProjectDir,cleanUpload);
	}

	
}
