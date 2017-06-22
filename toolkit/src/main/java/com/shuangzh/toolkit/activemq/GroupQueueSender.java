package com.shuangzh.toolkit.activemq;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.JmsException;
import org.springframework.jms.core.JmsOperations;
import org.springframework.jms.core.MessageCreator;

/**
 * GroupQueueSender 实现的向一组队列中发送消息，同时轮流向多个broker中发送消息。
 * 
 * @author shuangzh
 * @since 2016-2-21
 * @version 0.1
 */
public class GroupQueueSender implements Service {

	private static final Logger logger = LoggerFactory.getLogger(GroupQueueSender.class);

	/**
	 * 消息队列组的组名
	 */
	private String groupQueueName = "TestGroup";

	/**
	 * 实际进行jms操作的PollingTemplate
	 */
	private PollingTemplate pollingTemplate = new PollingTemplate();

	/**
	 * JmsOperation 代理类的工厂 bean
	 */
	private PollingTemplateProxyFactoryBean pollingTemplateProxyFactoryBean = new PollingTemplateProxyFactoryBean();;

	/**
	 * pollingTemplate的代理对象
	 */
	private JmsOperations jmsOperations = null;

	/**
	 * 队列组名与subname的连接符号
	 */
	private String jointMark = "-";

	public long getSlowlyTime() {
		return this.pollingTemplateProxyFactoryBean.getSlowlyTime();
	}

	/**
	 * 设置处理时间上限， 如果处理时间大于slowlyTime,则该broker会暂时屏蔽掉，以免影响整体性能。
	 * 
	 * @param slowlyTime
	 */
	public void setSlowlyTime(long slowlyTime) {
		this.pollingTemplateProxyFactoryBean.setSlowlyTime(slowlyTime);
	}

	public int getSlowCount() {
		return this.pollingTemplateProxyFactoryBean.getSlowCount();
	}

	/**
	 * 当某个broker的慢发送次数到达此阈值时，此broker会暂时屏蔽。
	 * 
	 * @param slowCount
	 */
	public void setSlowCount(int slowCount) {
		this.pollingTemplateProxyFactoryBean.setSlowCount(slowCount);
	}

	public String getGroupQueueName() {
		return groupQueueName;
	}

	public long getSlowBrokerBlockTime() {
		return pollingTemplateProxyFactoryBean.getSlowBrokerBlockTime();
	}

	public void setSlowBrokerBlockTime(long slowBrokerBlockTime) {
		this.pollingTemplateProxyFactoryBean.setSlowBrokerBlockTime(slowBrokerBlockTime);
	}

	public void setGroupQueueName(String groupQueueName) {
		this.groupQueueName = groupQueueName;
	}

	public void setBrokerURLs(List<String> brokerURLs) {
		this.pollingTemplate.setBrokerURLs(brokerURLs);
	}

	public List<String> getBrokerURLs() {
		return pollingTemplate.getBrokerURLs();
	}

	public int getMaxConnections() {
		return pollingTemplate.getMaxConnections();
	}

	public void setMaxConnections(int maxConnections) {
		this.pollingTemplate.setMaxConnections(maxConnections);
	}

	public void start() throws Exception {

		logger.info("start GroupQueueSender");

		pollingTemplate.setDefaultQueueName(groupQueueName + jointMark + "default");

		pollingTemplate.start();

		pollingTemplateProxyFactoryBean.setPollingTemplate(pollingTemplate);
		jmsOperations = (JmsOperations) pollingTemplateProxyFactoryBean.getObject();
	}

	public void stop() throws Exception {
		this.pollingTemplate.stop();
		pollingTemplateProxyFactoryBean.stop();
	}

	public void send(MessageCreator messageCreator) throws JmsException {
		jmsOperations.send(messageCreator);
	}

	public void send(String subName, MessageCreator messageCreator) throws JmsException {
		jmsOperations.send(groupQueueName + jointMark + subName, messageCreator);
	}

	/**
	 * 一般情况下直接采用此方法发送消息， systemId不为null时，Jms消息TextMessage中增加StringProperty :
	 * systemcode={systemId} 用于选择消息。
	 * 
	 * @param message
	 * @param subQueueName
	 * @param systemId
	 */
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

		if (subQueueName != null && subQueueName.length() > 0) {
			this.send(subQueueName, messageCreator);
		} else {
			this.send(messageCreator);
		}
	}

	/**
	 * 重載sendMsg, 不设置 消息的StringProperty
	 * 
	 * @param message
	 * @param subQueueName
	 */
	public void sendMsg(String message, String subQueueName) {
		this.sendMsg(message, subQueueName, null);
	}

}
