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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.axe.util.JsonUtil;
import org.axe.util.LogUtil;

import com.nicemq.common.constant.ClientMatchMode;
import com.tunnel.common.constant.Constant;
import com.tunnel.common.util.CollectionUtil;

/**
 * 客户端通讯录
 */
public class TcpClientManager {
	public static void main(String[] args) {
		String[] tags = {"zhicheng","box","A24"};
		add(new TcpClient("1,2"+Constant.SPLIT_FLAG+Constant.SPLIT_FLAG+String.join(Constant.SPLIT_FLAG, tags),null));
		tags = new String[]{"zhicheng","app","user","111"};
		add(new TcpClient(String.join(Constant.SPLIT_FLAG, tags),null));
		tags = new String[]{"zhicheng","app","user","111"};
		add(new TcpClient(String.join(Constant.SPLIT_FLAG, tags),null));
		
		
		LogUtil.log("连接树结构：");
		LogUtil.log(getClientTagsTree());
		LogUtil.log("所有连接：");
		LogUtil.log(JsonUtil.toJson(getAllClientTags()));
		
		/*LogUtil.log("第一次查询结果：");
		Set<TcpClient> set = get(new String[]{"zhicheng"},ClientMatchMode.MORE_MATCH);
		if(CollectionUtil.isNotEmpty(set)){
			for(TcpClient el:set){
				LogUtil.log(el.getTags());
			}
		}

		remove(client);

		LogUtil.log("连接树结构：");
		LogUtil.log(getClientTagsTree());
		LogUtil.log("第二次查询结果：");
		set = get(new String[]{"zhicheng"},ClientMatchMode.MORE_MATCH);
		if(CollectionUtil.isNotEmpty(set)){
			for(TcpClient el:set){
				LogUtil.log(el.getTags());
			}
		}*/
	}
	
	/**
	 * 保存客户端连接的树结构
	 * 根据tags一层一层下探
	 * 必须上锁，避免往里添加client的时候，其他client掉线，又在删除
	 */
	private static TcpClientTreeBranch mainBranch = new TcpClientTreeBranch();
	
	/**
	 * 返回json格式的连接树，
	 * 能看出路径层次结构，但是看不出多少连接
	 */
	public static String getClientTagsTree(){
		Map<String,Object> result = new HashMap<>();
		buildClientTagsTree(result, mainBranch);
		return JsonUtil.toJson(result);
	}
	
	/**
	 * clients:{}
	 * router:{
	 * 			router:{}
	 * 		}
	 */
	private static void buildClientTagsTree(Map<String,Object> result,TcpClientTreeBranch branch){
		//clients
		int i = 1;
		for(TcpClient tc:branch.getClientSet()){
			result.put("CLIENT_"+i++, tc);
		}
		
		//router
		for(String tag:branch.keySet()){
			Map<String,Object> nextLevelResult = new HashMap<>();
			result.put("TAG_"+tag, nextLevelResult);
			buildClientTagsTree(nextLevelResult, branch.get(tag));
		}
	}
	
	/**
	 * 返回所有连接tags，对于客户端数量
	 */
	public static Map<String,Integer> getAllClientTags(){
		Map<String,Integer> tagsMap = new HashMap<>();
		getAllClientTags(tagsMap,mainBranch);
		return tagsMap;
	}
	
	private static void getAllClientTags(Map<String,Integer> tagsMap,TcpClientTreeBranch branch){
		for(TcpClient client:branch.getClientSet()){
			if(tagsMap.containsKey(client.getTags())){
				tagsMap.put(client.getTags(), tagsMap.get(client.getTags())+1);
			}else{
				tagsMap.put(client.getTags(), 1);
			}
		}
		for(TcpClientTreeBranch nextLevelBranch:branch.values()){
			getAllClientTags(tagsMap, nextLevelBranch);
		}
	}
	
	public static TcpClient add(TcpClient client){
		synchronized (mainBranch) {
//			LogUtil.log("online: "+client.getTags());
			addToTcpClientTree(client, mainBranch,0);
			return client;
		}
	}
	
	private static void addToTcpClientTree(TcpClient client,TcpClientTreeBranch branch,int clientTagIndex){
		if(clientTagIndex >= client.getTagsAry().length){
			//到头了
			branch.addTcpClient(client);
		}else{
			//没到头，那就继续往下存tag
			String tag = client.getTagsAry()[clientTagIndex];
			TcpClientTreeBranch nextLevelBranch = branch.get(tag);
			if(nextLevelBranch == null){
				nextLevelBranch = branch.addSubBranch(tag, new TcpClientTreeBranch());
			}
			addToTcpClientTree(client, nextLevelBranch, clientTagIndex+1);
		}
	}
	
	public static Set<TcpClient> get(String[] tags,ClientMatchMode mode){
		synchronized (mainBranch) {
			return getFromTcpClientTree(mainBranch, tags, 0, mode);
		}
	}
	
	private static Set<TcpClient> getFromTcpClientTree(TcpClientTreeBranch branch,String[] tags,int tagIndex,ClientMatchMode mode){
		if(tagIndex >= tags.length){
			//到头了
			Set<TcpClient> clientSet = branch.getClientSet();
			if(mode.equals(ClientMatchMode.FULL_MATCH)){
				//全匹配
				return clientSet;
			}else{
				//多匹配
				getMoreClientSet(clientSet,branch);
				return clientSet;
			}
		}else{
			//继续往下取
			String tag = tags[tagIndex];
			TcpClientTreeBranch nextLevelBranch = branch.get(tag);
			if(nextLevelBranch == null){
				//还没到头就取不到了
				return null;
			}else{
				return getFromTcpClientTree(nextLevelBranch, tags, tagIndex+1, mode);
			}
		}
	}
	
	private static void getMoreClientSet(Set<TcpClient> clientSet,TcpClientTreeBranch branch){
		for(TcpClientTreeBranch nextLevelBranch:branch.values()){
			clientSet.addAll(nextLevelBranch.getClientSet());
			getMoreClientSet(clientSet, nextLevelBranch);
		}
	}
	
	public static void remove(TcpClient client){
		synchronized (mainBranch) {
//			LogUtil.log("offline: "+client.getTags());
			removeFromTcpClientTree(client, mainBranch, 0);
		}
	}
	
	private static void removeFromTcpClientTree(TcpClient client,TcpClientTreeBranch branch,int tagIndex){
		if(tagIndex >= client.getTagsAry().length){
			//到头了
			branch.removeClient(client);
		}else{
			//继续往下查看
			String tag = client.getTagsAry()[tagIndex];
			TcpClientTreeBranch nextLevelBranch = branch.get(tag);
			if(nextLevelBranch != null){
				removeFromTcpClientTree(client, nextLevelBranch, tagIndex+1);
				if(CollectionUtil.isEmpty(nextLevelBranch.keySet()) && CollectionUtil.isEmpty(nextLevelBranch.getClientSet())){
					//如果下层已经没有client了，也没有下下层了，就删掉
					branch.remove(tag);
				}
			}
		}
	}
	
}
