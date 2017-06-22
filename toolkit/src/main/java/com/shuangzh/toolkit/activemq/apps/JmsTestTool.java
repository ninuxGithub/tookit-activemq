package com.shuangzh.toolkit.activemq.apps;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.springframework.jms.core.MessageCreator;

import com.shuangzh.toolkit.activemq.AdvancedGroupQueueSender;
import com.shuangzh.toolkit.activemq.GroupQueueMessageListenerContainer;
import com.shuangzh.toolkit.activemq.GroupQueueSender;


/**
 * 消息发送或接收的工具类，用于消息发送或接收消息测试。
 * @author hpcmsz
 *
 */
public class JmsTestTool {

	public List<String> brokerJmxUrls = new ArrayList<String>();
	public int containerNum = 1;
	public int consumerNum = 1;
	public int inteval = 500;
	List<GroupQueueMessageListenerContainer> containers;

	public int producerNum = 1;
	public int threadNum = 1;
	public int cycleNum = 1;
	public int load = 1000;
	public String groupName = "Test";
	public Set<String> subnames = new HashSet<String>();
	public List<String> brokerURLs = new ArrayList<String>();
	List<GroupQueueSender> senders;
	List<AdvancedGroupQueueSender> adSenders;
	private String loadString;
	
	private int tokenFactor;

	public static void main(String[] args) throws Exception {

		JmsTestTool rm = new JmsTestTool();

		if (args.length == 0) {
			Scanner sc = new Scanner(System.in);

			System.out.println("run as [producer],  [adproducer]or [consumer]:");
			String type = sc.nextLine();

			System.out.println("input BrokerUrls:");
			String urls = sc.nextLine();
			rm.brokerURLs.addAll(Arrays.asList(urls.split(",")));

			System.out.println("input GroupQueue Name:");
			rm.groupName = sc.nextLine();

			if ("producer".equals(type)) {
				System.out.println("GroupQueueSender Numbers:");
				rm.producerNum = Integer.parseInt(sc.nextLine());

				System.out.println("Per GroupQueueSender Thread Number:");
				rm.threadNum = Integer.parseInt(sc.nextLine());

				System.out.println("Per Message byts:");
				rm.load = Integer.parseInt(sc.nextLine());

				System.out.println("Cycle Numbers:");
				rm.cycleNum = Integer.parseInt(sc.nextLine());

				System.out.println("Sub Queue Names:");
				rm.subnames.add("default");
				rm.subnames.addAll(Arrays.asList(sc.nextLine()));

				rm.runProducer();
			} else if ("consumer".equals(type)) {

//				System.out.println("brokerJmxUrls:");
//				rm.brokerJmxUrls
//						.addAll(Arrays.asList(sc.nextLine().split(",")));

				System.out.println("Consumer Container Number:");
				rm.containerNum = Integer.parseInt(sc.nextLine());

				System.out.println("Consumer Number:");
				rm.consumerNum = Integer.parseInt(sc.nextLine());
				
				System.out.println("interval:");
				rm.inteval =  Integer.parseInt(sc.nextLine());

				rm.runConsumer();
				
				sc.next();
				
			}else if ("adproducer".equals(type))
			{
				System.out.println("GroupQueueSender Numbers:");
				rm.producerNum = Integer.parseInt(sc.nextLine());

				System.out.println("Per GroupQueueSender Thread Number:");
				rm.threadNum = Integer.parseInt(sc.nextLine());

				System.out.println("Per Message byts:");
				rm.load = Integer.parseInt(sc.nextLine());

				System.out.println("Cycle Numbers:");
				rm.cycleNum = Integer.parseInt(sc.nextLine());
				
				System.out.println("tokerFactor:");
				
				rm.tokenFactor = Integer.parseInt(sc.nextLine());
				
				rm.subnames.add("default");
				
				rm.runAdPorducer();
				
			}
			else {
				System.out.println("input error , programe exit");
				System.exit(0);
			}
		}
	}

	private void runConsumer() {
		containers = new ArrayList<GroupQueueMessageListenerContainer>();
		for (int i = 0; i < containerNum; i++) {
			GroupQueueMessageListenerContainer container = new GroupQueueMessageListenerContainer();

			// 不采用 jmx监控broker的队列
//			container.setBrokerJmxURLs(brokerJmxUrls);
			container.setBrokerURLs(brokerURLs);
			container.setGroupQueueName(groupName);
			container.setConcurrentConsumers(consumerNum);

			Listener lt = new Listener(i);
			container.setMessageListener(lt);
			container.start();

			containers.add(container);
		}
	}
	
	class Listener implements MessageListener {

		AtomicLong count = new AtomicLong();
		int index = 0;
		volatile int l = 0;
		volatile long begin = System.currentTimeMillis();
		volatile long end = System.currentTimeMillis();

		Listener(int i) {
			this.index = i;
		}

		@Override
		public void onMessage(Message message) {
			long c = count.incrementAndGet();
			if (c - l == inteval) {
				end = System.currentTimeMillis();
				l = (int) c;
				long time = end - begin;
				begin = end;
				float speed = inteval * 1000 / time;
				System.out.println("+");
				System.out.println("container [" + index
						+ "] consumer speed : " + speed +" per seconds");
			}
			if (message instanceof TextMessage) {
				try {
					String m = ((TextMessage) message).getText();
//					System.out.print(m.charAt(1) + " ");
				} catch (JMSException e) {
					e.printStackTrace();
				}
			}

		}

	}
	
	private void runAdPorducer() throws Exception{
		adSenders = new ArrayList<AdvancedGroupQueueSender>();
		for (int i = 0; i < producerNum; i++) {
			AdvancedGroupQueueSender s = new AdvancedGroupQueueSender();
			s.setGroupQueueName(groupName);
			s.setBrokerURLs(brokerURLs);
			s.setMaxConnection(1);
			s.setTokenFactor(tokenFactor);
			s.start();
			adSenders.add(s);
		}

		StringBuilder sb = new StringBuilder();
		Random r = new Random();
		for (int i = 0; i < load; i++) {
			sb.append('A' + r.nextInt(25));
		}
		loadString = sb.toString();
		for (int i = 0; i < adSenders.size(); i++) {
			for (int k = 0; k < threadNum; k++) {
				Thread t = new AdSendThread(i);
				t.setDaemon(false);
				t.start();
			}
		}
		
	}
	
	

	private void runProducer() throws Exception {
		senders = new ArrayList<GroupQueueSender>();
		for (int i = 0; i < producerNum; i++) {
			GroupQueueSender s = new GroupQueueSender();
			s.setGroupQueueName(groupName);
			s.setBrokerURLs(brokerURLs);
			s.setMaxConnections(1);
			s.start();
			
			senders.add(s);
		}

		StringBuilder sb = new StringBuilder();
		Random r = new Random();
		for (int i = 0; i < load; i++) {
			sb.append('A' + r.nextInt(25));
		}
		loadString = sb.toString();
		for (int i = 0; i < senders.size(); i++) {
			for (int k = 0; k < threadNum; k++) {
				Thread t = new SendThread(i);
				t.setDaemon(false);
				t.start();
			}
		}
	}
	
	class AdSendThread extends Thread{
		
		int i = 0;

		AdSendThread(int i) {
			this.i = i;
		}

		public void run() {
			AdvancedGroupQueueSender sender = adSenders.get(i);
			long start = System.currentTimeMillis();
			System.out.println(Thread.currentThread().getName() + " tid:"
					+ Thread.currentThread().getId() + " AdvancedGroupQueueSender start sending. at "
					+ start);
			for (int j = 0; j < cycleNum; j++) {
				for (String sub : subnames) {
					
					sender.send(sub, new MessageCreator() {
						@Override
						public Message createMessage(Session session)
								throws JMSException {
							TextMessage msg = session.createTextMessage();
							msg.setText(loadString);
							return msg;
						}
						
					});
				}

			}
			long end = System.currentTimeMillis();
			System.out.println(Thread.currentThread().getName() + " tid:"
					+ Thread.currentThread().getId() + " AdvancedGroupQueueSender stop sending. at "
					+ end);
			float time = (end - start) / 1000;
			float speed = cycleNum * 1000 / (end - start);
			System.out.println(Thread.currentThread().getName() + " tid:"
					+ Thread.currentThread().getId() + " AdvancedGroupQueueSender sending use " + time
					+ " seconds. speed = " + speed + " per queue, total speed = " + speed * subnames.size() );
		}
	}

	class SendThread extends Thread {
		int i = 0;

		SendThread(int i) {
			this.i = i;
		}

		public void run() {
			GroupQueueSender sender = senders.get(i);
			long start = System.currentTimeMillis();
			System.out.println(Thread.currentThread().getName() + " tid:"
					+ Thread.currentThread().getId() + " start sending. at "
					+ start);
			for (int j = 0; j < cycleNum; j++) {
				for (String sub : subnames) {
					
					sender.send(sub, new MessageCreator() {
						@Override
						public Message createMessage(Session session)
								throws JMSException {
							TextMessage msg = session.createTextMessage();
							msg.setText(loadString);
							return msg;
						}
						
					});
				}

			}
			long end = System.currentTimeMillis();
			System.out.println(Thread.currentThread().getName() + " tid:"
					+ Thread.currentThread().getId() + " stop sending. at "
					+ end);
			float time = (end - start) / 1000;
			float speed = cycleNum * 1000 / (end - start);
			System.out.println(Thread.currentThread().getName() + " tid:"
					+ Thread.currentThread().getId() + " sending use " + time
					+ " seconds. speed = " + speed + " per boroker, total speed = " + speed * subnames.size() );
		}

	}

}
