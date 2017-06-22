package com.shuangzh.toolkit.activemq;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.pool.PooledConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.jms.JmsException;
import org.springframework.jms.UncategorizedJmsException;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessageCreator;


/**
 * 实现对多个MQ Broker轮循发送消息，通过增加MQ Broker的数量，拓宽消息传输的通道。 单个MQ的接收消息能力大约为1万TPS, 部署4个MQ时，可以达到4万。
 * 可以实现MQ消息通道的横向扩展。
 * @author hpcmsz
 *
 */
public class AdvancedGroupQueueSender implements SmartLifecycle {

	private static final Logger logger = LoggerFactory.getLogger(AdvancedGroupQueueSender.class);

	/**
	 * 多个broker的Url列表
	 */
	private List<String> brokerURLs = null;

	/**
	 * 每个broker的最大连接数
	 */
	private int maxConnection = 1;

	/**
	 * 某个broker连接不上或出现异常时，该broker的token被屏蔽的时间，单位毫秒，默认为30秒
	 */
	private long freeTime = 30000;

	/**
	 * 组队列名字
	 */
	private String groupQueueName = "TestGroup";

	/**
	 * 每个broker的token数量 = maxConnection * tokenFactor;
	 */
	private int tokenFactor = 2;

	public List<String> getBrokerURLs() {
		return brokerURLs;
	}

	public void setBrokerURLs(List<String> brokerURLs) {
		this.brokerURLs = brokerURLs;
	}

	public int getMaxConnection() {
		return maxConnection;
	}

	public void setMaxConnection(int maxConnection) {
		this.maxConnection = maxConnection;
	}

	public long getFreeTime() {
		return freeTime;
	}

	public void setFreeTime(long freeTime) {
		this.freeTime = freeTime;
	}

	public String getGroupQueueName() {
		return groupQueueName;
	}

	public void setGroupQueueName(String groupQueueName) {
		this.groupQueueName = groupQueueName;
	}

	public int getTokenFactor() {
		return tokenFactor;
	}

	public void setTokenFactor(int tokenFactor) {
		this.tokenFactor = tokenFactor;
	}

	private String jointMark = "-";

	private ActiveMQQueue defaultQueue = null;

	private Map<String, JmsTemplate> jmsTemplateMap = new HashMap<String, JmsTemplate>();

	private Map<String, PooledConnectionFactory> PooledConnectionFactoryMap = new HashMap<String, PooledConnectionFactory>();

	public Map<String, Set<Token>> TokenMap = new HashMap<String, Set<Token>>();

	public BlockingQueue<Token> blockingQueue = new LinkedBlockingQueue<Token>();

	public Timer timer = new Timer();

	class RevertTask extends TimerTask {
		Token freeToken;

		RevertTask(Token t) {
			this.freeToken = t;
		}

		@Override
		public void run() {
			freeToken.inQueue();
			blockingQueue.offer(freeToken);
		}

	}

	class Token {
		int id;
		String brokerUrl;
		JmsTemplate jmstemplate;
		boolean inQueue = false;
		boolean isFree = true;

		long count = 0;
		long fcount = 0;

		Token(int id, String url, JmsTemplate template) {
			this.id = id;
			this.brokerUrl = url;
			this.jmstemplate = template;
		}

		void outQueue() {
			this.inQueue = false;
			this.count++;
		}

		void inQueue() {
			this.inQueue = true;
			this.isFree = false;
		}

		void free() {
			this.isFree = true;
			this.fcount++;
		}
	}

	public void sendMsg(final String message, String subQueueName) throws JmsException {
		this.sendMsg(message, subQueueName, null);
	}

	public void sendMsg(final String message, String subQueueName, String systemId) throws JmsException {
		logger.debug("sendmessage:{}", message);
		final Map<String, String> stringProperty = new HashMap<String, String>();
		if (systemId != null && systemId.length() > 0) {
			stringProperty.put("systemcode", systemId);
		}

		MessageCreator messageCreator = new MessageCreator() {
			@Override
			public Message createMessage(Session session) throws JMSException {
				TextMessage txtmsg = session.createTextMessage();
				txtmsg.setText(message);
				if (stringProperty != null && stringProperty.size() > 0) {
					for (String key : stringProperty.keySet()) {
						txtmsg.setStringProperty(key, stringProperty.get(key));
					}
				}
				return txtmsg;
			}
		};

		this.send(subQueueName, messageCreator);
	}

	public void send(String subName, MessageCreator messageCreator) throws JmsException {

		if (subName == null || subName.trim().length() <= 0) {
			throw new UncategorizedJmsException("queue's subname is null or blank");
		} else {
			subName = subName.trim();
		}

		Token token = null;
		boolean success = false;

		for (int k = 0; k < maxConnection * tokenFactor * brokerURLs.size(); k++) {
			try {
				token = blockingQueue.take();
				token.outQueue();

				token.jmstemplate.send(groupQueueName + jointMark + subName, messageCreator);
				success = true;

				token.inQueue();
				blockingQueue.offer(token);
				break;

			} catch (InterruptedException e) {
				e.printStackTrace();
				throw new UncategorizedJmsException("blockingQueue.take() interrupted, " + e.getMessage());
			} catch (JmsException e) {
				token.free();
				logger.warn("failed to send message to broker {}", token.brokerUrl);
				timer.schedule(new RevertTask(token), freeTime);
			}
		}

		if (success == false) {
			throw new UncategorizedJmsException("failed send message to  anyone of brokers, maybe brokers are shutdown");
		}
	}

	@Override
	public boolean isRunning() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void start() {
		assert (brokerURLs != null && brokerURLs.size() > 0);
		assert (groupQueueName != null && groupQueueName.trim().length() > 0);
		groupQueueName = groupQueueName.trim();
		assert (maxConnection > 0 && tokenFactor > 0);

		defaultQueue = new ActiveMQQueue(groupQueueName + jointMark + "default");

		for (String url : brokerURLs) {
			PooledConnectionFactory pooledConnectionFactory = new PooledConnectionFactory(new ActiveMQConnectionFactory(url));
			JmsTemplate jmsTemplate = new JmsTemplate(pooledConnectionFactory);
			jmsTemplate.setDefaultDestination(defaultQueue);

			PooledConnectionFactoryMap.put(url, pooledConnectionFactory);
			jmsTemplateMap.put(url, jmsTemplate);

			Set<Token> set = new HashSet<Token>();
			for (int i = 0; i < maxConnection * tokenFactor; i++) {
				Token token = new Token(i, url, jmsTemplate);
				set.add(token);
			}
			TokenMap.put(url, set);
		}

		for (String url : TokenMap.keySet()) {
			Set<Token> s = TokenMap.get(url);
			if (s != null) {
				for (Token t : s) {
					blockingQueue.offer(t);
				}
			}
		}
	}

	public String Statistics() {
		StringBuilder sb = new StringBuilder();
		for (String url : TokenMap.keySet()) {
			Set<Token> s = TokenMap.get(url);
			int c = 0;
			int f = 0;
			for (Token t : s) {
				c += t.count;
				f += t.fcount;
			}
			sb.append("url=").append(url);
			sb.append(",").append("cout=").append(c);
			sb.append(",").append("fcount=").append(f);
			sb.append(",").append("count-fcount=").append(c - f);
			sb.append("\r\n");
		}

		return sb.toString();
	}

	@Override
	public void stop() {
		for (PooledConnectionFactory c : PooledConnectionFactoryMap.values()) {
			c.stop();
			c.clear();
		}
		this.timer.cancel();
	}

	@Override
	public int getPhase() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public boolean isAutoStartup() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void stop(Runnable arg0) {
		// TODO Auto-generated method stub

	}

	public static void main(String[] args) {
		List<String> urls = new ArrayList<String>();
		urls.add("tcp://127.0.0.1:61616?jms.useAsyncSend=true");
		urls.add("tcp://127.0.0.1:61616");
		urls.add("tcp://127.0.0.1:61617");

		final AdvancedGroupQueueSender sender = new AdvancedGroupQueueSender();
		sender.setBrokerURLs(urls);
		sender.setMaxConnection(1);
		sender.setFreeTime(10000L);
		sender.setGroupQueueName("TGG");
		sender.setTokenFactor(2);
		sender.start();

		final int cycleNum = 1000;
		int threadNum = 4;

		class SendThread extends Thread {
			public void run() {
				long start = System.currentTimeMillis();
				System.out.println(Thread.currentThread().getName() + " tid:" + Thread.currentThread().getId() + " start sending. at " + start);
				for (int j = 0; j < cycleNum; j++) {
					sender.sendMsg("test message", "default", "systemid");
				}
				long end = System.currentTimeMillis();
				System.out.println(Thread.currentThread().getName() + " tid:" + Thread.currentThread().getId() + " stop sending. at " + end);
				float time = (end - start) / 1000;
				float speed = cycleNum * 1000 / (end - start);
				System.out.println(Thread.currentThread().getName() + " tid:" + Thread.currentThread().getId() + " sending use " + time + " seconds. speed = " + speed);
				System.out.print(sender.Statistics());
			}
		}

		for (int i = 0; i < threadNum; i++) {
			SendThread thd = new SendThread();
			thd.start();
		}

	}

}
