package com.shuangzh.toolkit.activemq.test;

import java.util.ArrayList;
import java.util.List;

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.springframework.jms.core.MessageCreator;

import com.shuangzh.toolkit.activemq.GroupQueueSender;

public class SenderTest2 {

	public static void main(String[] args) throws Exception {
		int snum = 2;
		int cnum = 2;
		int tnum = 4;
		int load = 1000;
		String loadString = null;
		List<String> brokerURLs = new ArrayList<String>();
		brokerURLs.add("tcp://127.0.0.1:61616??tcpNoDelay=true");
		String groupName = "GQTT";

		StringBuilder sb = new StringBuilder();
		for (int t = 0; t < load; t++) {
			sb.append('8');
		}

		loadString = sb.toString();

		for (int k = 0; k < snum; k++) {
			GroupQueueSender s = new GroupQueueSender();
			s.setBrokerURLs(brokerURLs);
			s.setGroupQueueName(groupName);
			s.setMaxConnections(cnum);
			s.start();
			for (int m = 0; m < tnum; m++) {
				Thread th = new SenderTest2.InTread(s, loadString);
				th.start();
			}
		}
		
		
//		for (int k = 0; k < snum; k++) {
//			GroupQueueSender s = new GroupQueueSender();
//			s.setBrokerURLs(brokerURLs);
//			s.setGroupQueueName(groupName+"0000");
//			s.setMaxConnections(cnum);
//			s.start();
//			for (int m = 0; m < tnum; m++) {
//				Thread th = new SenderTest2.InTread(s, loadString);
//				th.start();
//			}
//		}
		
		
		

	}

	static public class InTread extends Thread {

		int cycle = 50000;
		GroupQueueSender sender = null;
		String loadString = null;

		public InTread(GroupQueueSender s, String l) {
			this.sender = s;
			this.loadString = l;
		}

		public void run() {
			long start=System.currentTimeMillis();
			for (int i = 0; i < cycle; i++) {
				sender.send(new MessageCreator() {

					@Override
					public Message createMessage(Session arg0)
							throws JMSException {
						BytesMessage msg=arg0.createBytesMessage();
						msg.writeBytes(loadString.getBytes());
						return msg;
					}
				});
			}
			long end= System.currentTimeMillis();
			float time = (end -start);
			float s = cycle * 1000 / (end - start);
			System.out.println(Thread.currentThread().getName()+" finished. time= " + time+ " spead = " + s);

		}

	}

}
