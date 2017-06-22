package com.shuangzh.toolkit.activemq;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jms.Message;
import javax.jms.MessageListener;
import javax.management.MalformedObjectNameException;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.command.ActiveMQMessage;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.command.ActiveMQTopic;
import org.apache.activemq.command.DestinationInfo;
import org.apache.activemq.pool.PooledConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.jms.listener.DefaultMessageListenerContainer;

/**
 * GroupQueueMessageListenerContainer 可以同时对多个broker中有相同前缀的队列进行消费。
 * 将有相同前缀的队列归为同一队列组，
 * 通过jmx获得同一组的所有队列的队列名，然后创建DefaultMessageListenerContainer进行消费。
 * 
 * @author shuangzh
 * 
 */

public class GroupQueueMessageListenerContainer implements SmartLifecycle {

	private static final Logger logger = LoggerFactory
			.getLogger(GroupQueueMessageListenerContainer.class);

	/**
	 * activeMq broker的链接 url列表
	 */
	private List<String> brokerURLs;

	/**
	 * jmx访问地址，用于获取同一组的所有队列
	 */
	private List<String> brokerJmxURLs;
	/**
	 * 用于处理消息的 messageListener;
	 */
	private MessageListener messageListener;
	/**
	 * 消息选择器
	 */
	private String messageSelector;
	/**
	 * 队列组名
	 */
	private String GroupQueueName = "Test";

	/**
	 * 每个DefaultMessageListenerContainer的并发消费者数
	 */
	private int concurrentConsumers = 1;

	/**
	 * 每个broker的最大链接数
	 */
	private int maxConnections = 1;
	/**
	 * jmx获取队列名的时间间隔，单位毫秒
	 */
	private int interval = 30000;

	private String jointMark = "-";
	private int divide = 100;

	/**
	 * 制定配置 子队列的consumer数量， 配置字符串为如 beijin:3,hebei:10 此时
	 * GroupName-beijin队列将配置3个consumer, GroupName-hebei队列将配置10个consumer
	 */
	private String subQueueConsumerCfg = null;
	
	public String getSubQueueConsumerCfg() {
		return subQueueConsumerCfg;
	}

	public void setSubQueueConsumerCfg(String subQueueConsumerCfg) {
		this.subQueueConsumerCfg = subQueueConsumerCfg;
	}

	private Map<String, Integer> consumerCfgMap = new HashMap<String, Integer>();

	public Map<String, Integer> getConsumerCfgMap() {
		return consumerCfgMap;
	}

	public void setConsumerCfgMap(Map<String, Integer> consumerCfgMap) {
		this.consumerCfgMap = consumerCfgMap;
	}

	private volatile boolean isRunning = false;

	private Thread monitor = null;

	private String advisoryTopic = "ActiveMQ.Advisory.Queue";

	private AdvisoryListener advisoryListener = new AdvisoryListener();

	private Map<String, PooledConnectionFactory> pooledConnectionFactoryMap = new HashMap<String, PooledConnectionFactory>();
	private Set<String> AllQueueNames = new HashSet<String>();
	private Map<String, DefaultMessageListenerContainer> containerMap = new HashMap<String, DefaultMessageListenerContainer>();

	public int getConcurrentConsumers() {
		return concurrentConsumers;
	}

	public void setConcurrentConsumers(int concurrentConsumers) {
		this.concurrentConsumers = concurrentConsumers;
	}

	public String getMessageSelector() {
		return messageSelector;
	}

	public void setMessageSelector(String messageSelector) {
		this.messageSelector = messageSelector;
	}

	public MessageListener getMessageListener() {
		return messageListener;
	}

	public void setMessageListener(MessageListener messageListener) {
		this.messageListener = messageListener;
	}

	public List<String> getBrokerURLs() {
		return brokerURLs;
	}

	public void setBrokerURLs(List<String> brokerURLs) {
		this.brokerURLs = brokerURLs;
	}

	/**
	 * 
	 * 不采用jmx获取队列， 改用advisory message 获取队列名
	 *
	 * public List<String> getBrokerJmxURLs() { return brokerJmxURLs; }
	 * 
	 * public void setBrokerJmxURLs(List<String> brokerJmxURLs) {
	 * this.brokerJmxURLs = brokerJmxURLs; }
	 */

	public String getGroupQueueName() {
		return GroupQueueName;
	}

	public void setGroupQueueName(String groupQueueName) {
		GroupQueueName = groupQueueName;
	}

	/*
	 * 
	 * public int getInterval() { return interval; }
	 * 
	 * public void setInterval(int interval) { this.interval = interval; }
	 */

	public void start() {
		assert (brokerURLs != null && brokerURLs.size() > 0);
		assert (brokerJmxURLs != null && brokerJmxURLs.size() > 0);
		assert (messageListener != null);
		logger.info("start GroupQueueMessageListenerContainer ...");
		
		/**
		 * 配置subqueue 的consumer 数量
		 */
		if (subQueueConsumerCfg != null) {
			String[] cfgs = subQueueConsumerCfg.split(",");
			for (String cfg : cfgs) {
				cfg = cfg.trim();
				String[] ca = cfg.split(":");
				if (ca.length != 2) {
					logger.error(
							"subqueue consumer number configuration error. {}",
							cfg);
				} else {
					try {
						ca[0]=ca[0].trim();
						ca[1]=ca[1].trim();
						Integer n = Integer.parseInt(ca[1]);
						consumerCfgMap.put(ca[0], n);
					} catch (Exception e) {
						logger.error(
								"subqueue consumer number is not a number . {}",
								cfg, e);
					}
				}
			}
		}

		initConnectionFactory();

		/**
		 * 采用 advisory Message 获取broker上的所有队列
		 */
		startAdvisoryContainer();

		/**
		 * 注释掉，该用Advisor 方式获取 broker中的队列名
		 */
		// startMonitorThread();

		isRunning = true;
	}

	private void startAdvisoryContainer() {
		for (String brokerUrl : brokerURLs) {
			DefaultMessageListenerContainer container = new DefaultMessageListenerContainer();
			container.setConnectionFactory(pooledConnectionFactoryMap
					.get(brokerUrl));
			container.setConcurrentConsumers(1);
			container.setDestination(new ActiveMQTopic(advisoryTopic));
			container.setMessageListener(advisoryListener);
			container.initialize();
			container.start();

			String key = brokerUrl + "," + advisoryTopic;
			containerMap.put(key, container);
			logger.info("container for advisory queue topic [{}] had created",
					key);
		}
	}

	class AdvisoryListener implements MessageListener {

		@Override
		public void onMessage(Message message) {

			logger.info("AdvisoryListener receive message");
			if (message instanceof ActiveMQMessage) {
				ActiveMQMessage aMsg = (ActiveMQMessage) message;
				DestinationInfo desInf = (DestinationInfo) aMsg
						.getDataStructure();

				String queueName = desInf.getDestination().getPhysicalName();
				if (queueName != null && desInf.isAddOperation()) {
					addContainer(queueName);
				}
			} else {
				logger.error("AdvisoryListener recieve message is not ActiveMQMessage, that is abnormal.");
			}

		}

		synchronized public void addContainer(String queueName) {
			String prefix = GroupQueueName + jointMark;
			if (queueName.startsWith(prefix)) {
				if (!AllQueueNames.contains(queueName)) {
					AllQueueNames.add(queueName);
					logger.info("discover new queue {}", queueName);
				}
			}
			updateContainers();
		}
	}

	private void initConnectionFactory() {
		for (String url : brokerURLs) {
			ActiveMQConnectionFactory activeMQConnectionFactory = new ActiveMQConnectionFactory();
			activeMQConnectionFactory.setBrokerURL(url);

			PooledConnectionFactory pooledConnectionFactory = new PooledConnectionFactory();
			pooledConnectionFactory
					.setConnectionFactory(activeMQConnectionFactory);
			pooledConnectionFactory.setMaxConnections(maxConnections);
			pooledConnectionFactoryMap.put(url, pooledConnectionFactory);
		}
	}

	synchronized private void updateContainers() {
		for (String brokerUrl : brokerURLs) {
			for (String name : AllQueueNames) {
				String key = brokerUrl + "," + name;
				DefaultMessageListenerContainer container = containerMap
						.get(key);
				if (container == null) {
					logger.warn(
							"there is no DefaultMessageListenerContainer for queue {}, new containner will be created",
							key);
					container = new DefaultMessageListenerContainer();
					container.setConnectionFactory(pooledConnectionFactoryMap
							.get(brokerUrl));
					
					
					/**
					 * 设置subQueue的consumers数量
					 */
					String subname = name.replaceFirst(GroupQueueName
							+ jointMark, "");
					subname = subname.trim();
					Integer n = consumerCfgMap.get(subname);
					if (n != null) {
						container.setConcurrentConsumers(n);
					} else {
						container.setConcurrentConsumers(concurrentConsumers);
					}

					

					container.setMessageSelector(messageSelector);
					container.setDestination(new ActiveMQQueue(name));
					container.setMessageListener(messageListener);
					container.initialize();
					container.start();

					containerMap.put(key, container);
					logger.info(
							"DefaultMessageListenerContainer for queue {} had created",
							key);
				}
			}
		}
	}

	/**
	 * 
	 * 不再启用 jmx监控线程获取队列名 , 改用advisory mesaage 获取 队列名
	 * 
	 *
	 * private void updateQueueNames() throws MalformedObjectNameException,
	 * IOException {
	 * 
	 * for (String jmxUrl : brokerJmxURLs) { List<String> queueNames =
	 * JmxTool.getQueueNames(jmxUrl); String prefix = GroupQueueName +
	 * jointMark; for (String name : queueNames) { if (name.startsWith(prefix))
	 * { if (!AllQueueNames.contains(name)) { AllQueueNames.add(name);
	 * logger.info("discover new queue {}", name); } } } } }
	 */

	/*
	 * 不再启用 jmx监控线程获取队列名
	 * 
	 * private void startMonitorThread() { monitor = new Thread(new Runnable() {
	 * public void run() { logger.info("MonitorThread running ..."); int plus =
	 * interval / divide; int sleeptime = 0; isRunning = true; while (true) {
	 * logger.debug("monitor cylce to discover queues"); if (isRunning == false)
	 * break;
	 * 
	 * if (sleeptime >= interval) { sleeptime = interval; } else { sleeptime +=
	 * plus; }
	 * 
	 * try { updateQueueNames(); } catch (MalformedObjectNameException |
	 * IOException e) { logger.error("can't update queue names", e); }
	 * 
	 * updateContainers();
	 * 
	 * try { Thread.sleep(sleeptime); } catch (InterruptedException e) {
	 * logger.warn("monitor is interrupted", e); if (isRunning == false) break;
	 * } } } });
	 * 
	 * monitor.setDaemon(true); monitor.start(); }
	 */

	synchronized public void stop() {
		for (DefaultMessageListenerContainer container : containerMap.values()) {
			container.stop();
		}

		for (PooledConnectionFactory factory : pooledConnectionFactoryMap
				.values()) {
			factory.stop();
		}
		isRunning = false;
	}

	public boolean isRunning() {
		// TODO Auto-generated method stub
		return this.isRunning;
	}

	public int getPhase() {
		// TODO Auto-generated method stub
		return 0;
	}

	public boolean isAutoStartup() {
		// TODO Auto-generated method stub
		return true;
	}

	public void stop(Runnable callback) {
		// TODO Auto-generated method stub
	}

}
