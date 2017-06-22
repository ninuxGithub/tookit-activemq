package com.shuangzh.toolkit.activemq;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.Queue;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.Service;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.pool.PooledConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.JmsException;
import org.springframework.jms.UncategorizedJmsException;
import org.springframework.jms.core.BrowserCallback;
import org.springframework.jms.core.JmsOperations;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessageCreator;
import org.springframework.jms.core.MessagePostProcessor;
import org.springframework.jms.core.ProducerCallback;
import org.springframework.jms.core.SessionCallback;

/**
 * PollingTemplate 轮流像多个broker发送或消费消息. PollingTemplate 为每个broker
 * url地址生成一个jmstemplate,形成一个jmstemplate组，发送消息时轮流发送到不同的broker上。
 * 
 * @author shuangzh
 * @version 0.1
 * @since 2016-2-21
 */
public class PollingTemplate implements Service, JmsOperations {

	private static final Logger logger = LoggerFactory
			.getLogger(PollingTemplate.class);
	/**
	 * broker地址列表
	 */
	private List<String> brokerURLs = null;
	/**
	 * 链接池的链接数
	 */
	private int maxConnections = 1;
	/**
	 * 默认队列名字
	 */
	private String defaultQueueName = "Test";

	protected int brokerNum = 0;
	private AtomicLong count = new AtomicLong();
	private ActiveMQQueue defaultActiveMQQueue = null;
	protected List<JmsTemplate> JmsTemplateList = null;
	
	protected final Map<Integer, JmsTemplate> allTemplateMap=new HashMap<Integer, JmsTemplate>();

	public Map<Integer, JmsTemplate> getAllTemplateMap() {
		return allTemplateMap;
	}

	public List<JmsTemplate> getJmsTemplateList() {
		return JmsTemplateList;
	}

	public void setJmsTemplateList(List<JmsTemplate> jmsTemplateList) {
		JmsTemplateList = jmsTemplateList;
	}

	private List<PooledConnectionFactory> pooledConnectionFactoryList = null;

	private ThreadLocal<String> distinationBrokerUrl = new ThreadLocal<String>();
	private ThreadLocal<Integer> distinationBrokerIndex = new ThreadLocal<Integer>() {
		public Integer initialValue() {
			return new Integer(-1);
		};
	};

	public String getLastlyBrokerUrl() {
		return distinationBrokerUrl.get();
	}

	public int getLastlyBrokerIndex() {
		return distinationBrokerIndex.get();
	}

	public List<String> getBrokerURLs() {
		return brokerURLs;
	}

	public void setBrokerURLs(List<String> brokerURLs) {
		this.brokerURLs = brokerURLs;
	}

	public String getDefaultQueueName() {
		return defaultQueueName;
	}

	public void setDefaultQueueName(String defaultQueueName) {
		this.defaultQueueName = defaultQueueName;
	}

	public int getMaxConnections() {
		return maxConnections;
	}

	public void setMaxConnections(int maxConnections) {
		this.maxConnections = maxConnections;
	}

	public void start() throws Exception {

		logger.info("start PollingTemplate");

		assert (brokerURLs != null);
		assert (brokerURLs.size() > 0);
		assert (defaultQueueName != null && defaultQueueName.length() > 0);

		brokerNum = brokerURLs.size();
		defaultActiveMQQueue = new ActiveMQQueue(defaultQueueName);
		pooledConnectionFactoryList = new ArrayList<PooledConnectionFactory>();
		JmsTemplateList = new ArrayList<JmsTemplate>();

		for (int index = 0; index < brokerNum; index++) {
			ActiveMQConnectionFactory activeMQConnectionFactory = new ActiveMQConnectionFactory();
			activeMQConnectionFactory.setBrokerURL(brokerURLs.get(index));

			// 在连接字符串中增加 jms.useAsyncSend=true 可以有同样的作用
			// activeMQConnectionFactory.setUseAsyncSend(true);

			PooledConnectionFactory pooledConnectionFactory = new PooledConnectionFactory();
			pooledConnectionFactory
					.setConnectionFactory(activeMQConnectionFactory);
			pooledConnectionFactory.setMaxConnections(maxConnections);

			JmsTemplate jmsTemplate = new JmsTemplate();
			jmsTemplate.setConnectionFactory(pooledConnectionFactory);
			jmsTemplate.setDefaultDestination(defaultActiveMQQueue);

			pooledConnectionFactoryList.add(pooledConnectionFactory);
			JmsTemplateList.add(jmsTemplate);
			
			allTemplateMap.put(index, jmsTemplate);

			/**
			 * 启动时验证broker Url是否可用
			 */
			try {
				logger.info("connect to broker url [{}]", brokerURLs.get(index));
				Connection con = pooledConnectionFactory.createConnection();
				con.start();
				con.close();
				logger.debug("broker [{}] pooledConnectionFactory has [{}] Connetions in pool",  brokerURLs.get(index), pooledConnectionFactory.getNumConnections());
				
			} catch (Exception e) {
				logger.error("connect to broker failed , broker url [{}]",
						brokerURLs.get(index));
				logger.debug("connect to broker failed , broker url [{}]",
						brokerURLs.get(index), e);
			}
		}
		logger.debug("JmsTemplateList has {} element", JmsTemplateList.size());
	}

	public void stop() throws Exception {
		logger.info("stop PollingTemplate");
		for (PooledConnectionFactory item : pooledConnectionFactoryList) {
			item.stop();
		}
	}

	/**
	 * 轮流从 JmsTemplate列表中取出jsmTemplate
	 * 
	 * @return
	 */
	protected JmsTemplate pickJmsTemplate() throws UncategorizedJmsException {
		long c = count.getAndIncrement();
		JmsTemplate template = null;
		for (long j = c; j < c + brokerNum; j++) {
			int index = (int) (j % brokerNum);
			template = JmsTemplateList.get(index);
			if (template != null) {
				distinationBrokerUrl.set(brokerURLs.get(index));
				distinationBrokerIndex.set(index);
				break;
			}
		}

		if (template == null) {
			distinationBrokerUrl.set(null);
			distinationBrokerIndex.set(-1);
			throw new UncategorizedJmsException(
					"JmsTemplateList's items are null, maybe the jmstemplates are not avaliabe for the monment");
		}
		return template;
	}

	/**
	 * 获得最后一次 操作时的目标brokerUrl地址，从而可以知道消息是发送到了哪个broker中
	 * 
	 * @return
	 */
	public String getLastOperationDistinationBrokerUrl() {
		return distinationBrokerUrl.get();
	}

	public <T> T execute(SessionCallback<T> action) throws JmsException {
		// TODO Auto-generated method stub
		return null;
	}

	public <T> T execute(ProducerCallback<T> action) throws JmsException {
		// TODO Auto-generated method stub
		return null;
	}

	public <T> T execute(Destination destination, ProducerCallback<T> action)
			throws JmsException {
		// TODO Auto-generated method stub
		return null;
	}

	public <T> T execute(String destinationName, ProducerCallback<T> action)
			throws JmsException {
		// TODO Auto-generated method stub
		return null;
	}

	public void send(MessageCreator messageCreator) throws JmsException {
		this.pickJmsTemplate().send(messageCreator);

	}

	public void send(Destination destination, MessageCreator messageCreator)
			throws JmsException {
		this.pickJmsTemplate().send(destination, messageCreator);
	}

	public void send(String destinationName, MessageCreator messageCreator)
			throws JmsException {
		this.pickJmsTemplate().send(destinationName, messageCreator);
	}

	public void convertAndSend(Object message) throws JmsException {
		this.pickJmsTemplate().convertAndSend(message);

	}

	public void convertAndSend(Destination destination, Object message)
			throws JmsException {
		this.pickJmsTemplate().convertAndSend(destination, message);

	}

	public void convertAndSend(String destinationName, Object message)
			throws JmsException {
		this.pickJmsTemplate().convertAndSend(destinationName, message);
	}

	public void convertAndSend(Object message,
			MessagePostProcessor postProcessor) throws JmsException {
		this.pickJmsTemplate().convertAndSend(message, postProcessor);
	}

	public void convertAndSend(Destination destination, Object message,
			MessagePostProcessor postProcessor) throws JmsException {
		this.pickJmsTemplate().convertAndSend(destination, message,
				postProcessor);
	}

	public void convertAndSend(String destinationName, Object message,
			MessagePostProcessor postProcessor) throws JmsException {
		this.pickJmsTemplate().convertAndSend(destinationName, message,
				postProcessor);
	}

	public Message receive() throws JmsException {
		return this.pickJmsTemplate().receive();
	}

	public Message receive(Destination destination) throws JmsException {
		return this.pickJmsTemplate().receive(destination);
	}

	public Message receive(String destinationName) throws JmsException {
		return this.pickJmsTemplate().receive(destinationName);
	}

	public Message receiveSelected(String messageSelector) throws JmsException {
		return this.pickJmsTemplate().receiveSelected(messageSelector);
	}

	public Message receiveSelected(Destination destination,
			String messageSelector) throws JmsException {
		return this.pickJmsTemplate().receiveSelected(destination,
				messageSelector);
	}

	public Message receiveSelected(String destinationName,
			String messageSelector) throws JmsException {
		return this.pickJmsTemplate().receiveSelected(destinationName,
				messageSelector);
	}

	public Object receiveAndConvert() throws JmsException {
		return this.pickJmsTemplate().receiveAndConvert();
	}

	public Object receiveAndConvert(Destination destination)
			throws JmsException {
		return this.pickJmsTemplate().receiveAndConvert(destination);
	}

	public Object receiveAndConvert(String destinationName) throws JmsException {
		return this.pickJmsTemplate().receiveAndConvert(destinationName);
	}

	public Object receiveSelectedAndConvert(String messageSelector)
			throws JmsException {
		return this.pickJmsTemplate()
				.receiveSelectedAndConvert(messageSelector);
	}

	public Object receiveSelectedAndConvert(Destination destination,
			String messageSelector) throws JmsException {
		return this.pickJmsTemplate().receiveSelectedAndConvert(destination,
				messageSelector);
	}

	public Object receiveSelectedAndConvert(String destinationName,
			String messageSelector) throws JmsException {
		return this.pickJmsTemplate().receiveSelectedAndConvert(
				destinationName, messageSelector);
	}

	public Message sendAndReceive(MessageCreator messageCreator)
			throws JmsException {
		// TODO Auto-generated method stub
		return null;
	}

	public Message sendAndReceive(Destination destination,
			MessageCreator messageCreator) throws JmsException {
		// TODO Auto-generated method stub
		return null;
	}

	public Message sendAndReceive(String destinationName,
			MessageCreator messageCreator) throws JmsException {
		// TODO Auto-generated method stub
		return null;
	}

	public <T> T browse(BrowserCallback<T> action) throws JmsException {
		// TODO Auto-generated method stub
		return null;
	}

	public <T> T browse(Queue queue, BrowserCallback<T> action)
			throws JmsException {
		// TODO Auto-generated method stub
		return null;
	}

	public <T> T browse(String queueName, BrowserCallback<T> action)
			throws JmsException {
		// TODO Auto-generated method stub
		return null;
	}

	public <T> T browseSelected(String messageSelector,
			BrowserCallback<T> action) throws JmsException {
		// TODO Auto-generated method stub
		return null;
	}

	public <T> T browseSelected(Queue queue, String messageSelector,
			BrowserCallback<T> action) throws JmsException {
		// TODO Auto-generated method stub
		return null;
	}

	public <T> T browseSelected(String queueName, String messageSelector,
			BrowserCallback<T> action) throws JmsException {
		// TODO Auto-generated method stub
		return null;
	}

}
