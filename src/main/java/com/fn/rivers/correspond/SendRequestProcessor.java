package com.fn.rivers.correspond;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.fn.rivers.GlobalParam;
import com.fn.rivers.GlobalParam.MESSAGE_SEND_TYPE;
import com.fn.rivers.GlobalParam.MESSAGE_TYPE;
import com.fn.rivers.server.LeaderMaintain;
import com.fn.rivers.server.Server;
import com.fn.rivers.server.ServerMaintain;
import com.fn.rivers.util.IpV4Util;;

/**
 * 
 * @author chengwen
 *
 */
public class SendRequestProcessor { 

	public BlockingQueue<Request> uni_messages = new LinkedBlockingQueue<>();

	public BlockingQueue<Request> broad_messages = new LinkedBlockingQueue<>();

	private MulticastSocket BCsocket;
	
	private HashMap<String, Socket> UNIsocket = new HashMap<>();

	private InetAddress BCAddr;

	public void init(MulticastSocket socket, InetAddress addr) {
		this.BCsocket = socket;
		this.BCAddr = addr;
		new Thread(() -> {
			while (true) {
				try {
					uniCast(uni_messages.take());
				} catch (InterruptedException e) {
					GlobalParam.LOG.error("Unicast Message InterruptedException", e);
				}
			}
		}).start();

		new Thread(() -> {
			while (true) {
				try {
					broadCast(broad_messages.take());
				} catch (InterruptedException e) {
					GlobalParam.LOG.error("Broadcast Message InterruptedException", e);
				}
			}
		}).start();
	}

	public void put(Request rq, MESSAGE_SEND_TYPE type) {
		try {
			if (type == MESSAGE_SEND_TYPE.UNICAST) {
				this.uni_messages.put(rq);
			} else {
				this.broad_messages.put(rq);
			}
		} catch (InterruptedException e) {
			GlobalParam.LOG.error("Message Queue InterruptedException", e);
		}
	}

	private void broadCast(Request rq) {
		try {
			DatagramPacket outpack = new DatagramPacket(new byte[0], 0, this.BCAddr, GlobalParam.BC_PORT);
			outpack.setData(messageOut(rq).toByteArray());
			this.BCsocket.send(outpack);
			if(rq.getType().getVal()>199) {
				Iterator<Entry<String,Server>> etr = GlobalParam.CLOUD_NODES.entrySet().iterator();
				while(etr.hasNext()) {
					Server r = etr.next().getValue();
					if(r.isOnline.get() && IpV4Util.isSameAddress(r.getIp(), GlobalParam.NODE_IP)) {
						if(GlobalParam.CLOUD_HOSTS.contains(r.getIp())) {
							rq.setDestinationIp(GlobalParam.UNI_PORT, r.getIp());
							uniCast(rq);
						}
					}
				} 
			} 
		} catch (Exception e) {
			GlobalParam.LOG.error("Broad Cast Service Exception", e);
		}
	}

	private void uniCast(Request rq) {
		if(rq.getDestinationIp().equals(GlobalParam.NODE_IP))
			return;
		try {
			if(!UNIsocket.containsKey(rq.getDestinationIp())) { 
				UNIsocket.put(rq.getDestinationIp(),getSocket(rq));
			}else {
				if(UNIsocket.get(rq.getDestinationIp()).isClosed()) {
					UNIsocket.get(rq.getDestinationIp()).close();
					UNIsocket.put(rq.getDestinationIp(),getSocket(rq));
				}
			}
			OutputStream os = UNIsocket.get(rq.getDestinationIp()).getOutputStream();
			os.write(messageOut(rq).toByteArray());
			os.flush();
			os.close(); 
		}catch (Exception e) { 
			GlobalParam.LOG.warn(rq.getDestinationIp() + " message deliver failed!");
			if(rq.getType().getVal()<200) {
				ServerMaintain.serverRemove(rq.getDestinationIp());
			}else if(rq.getType()==MESSAGE_TYPE.LEADER_LIVECHECK) {
				LeaderMaintain.leaderCheck();
			}    
		} 
	}
	
	private Socket getSocket(Request rq) throws Exception {
		Socket sk = new Socket();
		sk.connect(new InetSocketAddress(rq.getDestinationIp(), rq.getPort()),GlobalParam.nodeHeartBeatTime);
		return sk;
	}

	private ByteArrayOutputStream messageOut(Request rq) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ObjectOutputStream oos = new ObjectOutputStream(bos);
		oos.writeObject(rq);
		return bos;
	}
}
