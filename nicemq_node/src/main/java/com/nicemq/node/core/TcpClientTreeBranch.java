package com.nicemq.node.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * 用于构建客户端连接的保存栈，树结构
 * 每个节点，既可以存一系列tag，也可以存client集合，集合空表示这个节点都是tag，集合可以只有1个，一般情况下，1对1绑定就是这样
 * 结构如下：
 * node1->node1_1->client?
 * 		->node1_2->node1_2_1->client?
 * 		->client?
 */
public class TcpClientTreeBranch extends HashMap<String, TcpClientTreeBranch> {
	private static final long serialVersionUID = 1l;

	//有TcpClient的话，那就是有连接的路径
	private Set<TcpClient> clientSet = new HashSet<>();

	public void addTcpClient(TcpClient client) {
		clientSet.add(client);
	}

	public Set<TcpClient> getClientSet() {
		// 返回是拷贝集合，禁止修改原集合
		Set<TcpClient> newSet = new HashSet<>();
		newSet.addAll(clientSet);
		return newSet;
	}

	/**
	 * 名称不那么合适，被addSubBranch代替
	 */
	@Override
	@Deprecated
	public TcpClientTreeBranch put(String key, TcpClientTreeBranch value) {
		return super.put(key, value);
	}

	/**
	 * 添加下级路径
	 */
	public TcpClientTreeBranch addSubBranch(String tag, TcpClientTreeBranch branch) {
		super.put(tag, branch);
		return branch;
	}

	public void removeClient(TcpClient client) {
		clientSet.remove(client);
	}
}