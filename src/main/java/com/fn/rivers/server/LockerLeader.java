package com.fn.rivers.server;

import com.fn.rivers.GlobalParam;
import com.fn.rivers.GlobalParam.MESSAGE_SEND_TYPE;
import com.fn.rivers.GlobalParam.MESSAGE_TYPE;
import com.fn.rivers.correspond.Request;

/**
 * 
 * @author chenwen
 *
 */
public class LockerLeader implements Locker { 
	
	public LockerLeader() { 
		new ServerMaintain().start(); 
		new LeaderMaintain().start();
		GlobalParam.LOG.info("River Lock start success with Leader mode!");
	}

	public boolean acquire(String LockName) {
		if (LockName != null) {
			Server sev = checkIsReady();
			if (sev != null) {
				if(GlobalParam.CLOUD_NODES.isLeader()) {
					if(GlobalParam.lockerHoldNodes.getAndSet(LockName).size()==0) {
						GlobalParam.lockerHoldNodes.addAndSet(LockName, new Server(GlobalParam.NODE_IP));
						return true;
					}else {
						return false;
					} 
				}else {
					GlobalParam.SendRequestProcessor.put(new Request(MESSAGE_TYPE.LEADER_LOCKREQUEST, GlobalParam.UNI_PORT, sev.getIp(), LockName, null),
							MESSAGE_SEND_TYPE.UNICAST);
				}
			} else {
				LeaderMaintain.leaderCheck();
			}
		}
		return false;
	}

	public boolean release(String LockName) {
		if(GlobalParam.CLOUD_NODES.isLeader()) {
			GlobalParam.lockerHoldNodes.removeAndSet(LockName, GlobalParam.NODE_IP);
		}else {
			GlobalParam.SendRequestProcessor.put(new Request(MESSAGE_TYPE.LEADER_LOCKRELEASE, GlobalParam.UNI_PORT, GlobalParam.NODE_IP, LockName, null),
					MESSAGE_SEND_TYPE.UNICAST);
		}
		return false;
	}

	private Server checkIsReady() {
		if (GlobalParam.SendRequestProcessor != null
				&& GlobalParam.CLOUD_NODES.liveNums() >= GlobalParam.mininum_nodes) {
			return GlobalParam.CLOUD_NODES.getLeader();
		} else {
			GlobalParam.LOG.info("Cloud start nodes not match mininum_nodes or leader not elect!");
			return null;
		}
	}

}
