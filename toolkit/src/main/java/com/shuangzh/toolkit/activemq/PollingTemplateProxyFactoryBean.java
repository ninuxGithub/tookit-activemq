package com.shuangzh.toolkit.activemq;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.jms.UncategorizedJmsException;
import org.springframework.jms.core.JmsOperations;
import org.springframework.jms.core.JmsTemplate;

/**
 * PollingTemplateProxyFactoryBean 为PollingTemplate生成JmsOperations代理类。
 * 代理类中增加了重试功能， 如果一个向一个broker发送消息失败，则会向另外的broker在重新发送。
 * 
 * @author shuangzh
 *
 */
public class PollingTemplateProxyFactoryBean implements FactoryBean, Service {

	private static final Logger logger = LoggerFactory.getLogger(PollingTemplateProxyFactoryBean.class);
	/**
	 * 被代理的 pollingTemplate
	 */
	private PollingTemplate pollingTemplate = null;

	/**
	 * 最大尝试次数，默认为broker的数目地两倍
	 */
	private int tryTimes = 1;

	/**
	 * 暂时不可用的broker 的JmsTemplate放到此map中
	 */
	private final Map<Integer, JmsTemplate> UnavailableBrokers = new ConcurrentHashMap<Integer, JmsTemplate>();

	/**
	 * 定时器，将暂时不可用的JmsTemplate 放回到 pollingTemplate的JmsTemplate中，以便再尝试连接。
	 */
	public Timer timer = new Timer();

	/**
	 * timer 定时器的定时任务的延迟时间 单位毫秒。
	 */
	private long reConnectTime = 10000;

	/**
	 * 定时任务的delay时间，单位毫秒
	 * 
	 * @return
	 */
	public long getReConnectTime() {
		return reConnectTime;
	}

	/**
	 * 设置定时器的任务执行延迟时间，重试连接Broker的时间间隔
	 * 
	 * @param reConnectTime
	 */
	public void setReConnectTime(long reConnectTime) {
		this.reConnectTime = reConnectTime;
	}

	/**
	 * 当broker处理消息缓慢时， 一般情况下是broker出现问题，此时需要排除可能有问题的broker.
	 * 当客户端向broker发送消息时，如果耗费的时间大于 slowlyTime那么将排除次broker.
	 * 默认为3毫秒，即broker每秒处理500次消息以上才正常，否则视为broker有问题。
	 */
	private long slowlyTime = 2;

	public long getSlowlyTime() {
		return slowlyTime;
	}

	public void setSlowlyTime(long slowlyTime) {
		this.slowlyTime = slowlyTime;
	}

	/**
	 * 慢broker计数器的阈值，当慢队列处理次数大道 slowCount时，此broker的jmsTemplate。
	 */
	private int slowCount = 3;

	public int getSlowCount() {
		return slowCount;
	}

	public void setSlowCount(int slowCount) {
		this.slowCount = slowCount;
	}

	/**
	 * 处理慢的broker的屏蔽时间，默认为100毫秒
	 */
	private long slowBrokerBlockTime = 100;

	public long getSlowBrokerBlockTime() {
		return slowBrokerBlockTime;
	}

	public void setSlowBrokerBlockTime(long slowBrokerBlockTime) {
		this.slowBrokerBlockTime = slowBrokerBlockTime;
	}

	private final Map<String, AtomicInteger> slowCountMap = new ConcurrentHashMap<String, AtomicInteger>();

	private final Map<Integer, JmsTemplate> slowBrokers = new ConcurrentHashMap<Integer, JmsTemplate>();

	private List<JmsTemplate> allTemplate;

	/**
	 * 定时任务，将暂时不可用的JmsTemplate 返回到pollingTemplate的JmsTemplateList中
	 * 
	 * @author shuangzh
	 *
	 */
	class RevertTast extends TimerTask {

		@Override
		public void run() {
			logger.info("RevertTask timer running");
			for (Integer i : UnavailableBrokers.keySet()) {
				JmsTemplate temp = UnavailableBrokers.remove(i);
				if (temp != null) {
					pollingTemplate.getJmsTemplateList().set(i, temp);
					logger.info("JmsTemplate [{}] put back to pollingTemplate's jmsTemplateList", i);
				} else {
					logger.warn(" UnavailableBrokers key [{}] value is null, maybe is removed", i);
				}
			}
		}
	}

	class RevertSlowBrokerTask extends TimerTask {
		int index;

		RevertSlowBrokerTask(int i) {
			this.index = i;
		}

		@Override
		public void run() {
			logger.info("RevertSlowBrokerTask timer running");
			JmsTemplate temp = slowBrokers.remove(this.index);
			if (temp != null) {
				pollingTemplate.getJmsTemplateList().set(index, temp);
				logger.info("In RevertSlowBrokerTask, JmsTemplate [{}] put back to pollingTemplate's list", index);
			}
		}
	}

	class ProxyHandler implements InvocationHandler {

		private final Logger log = LoggerFactory.getLogger(ProxyHandler.class);

		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			Object object = null;

			long time = 0L;

			boolean sucess = false;
			for (int t = 0; t < tryTimes; t++) {
				try {
					time = System.currentTimeMillis();

					object = method.invoke(pollingTemplate, args);
					sucess = true;

					time = System.currentTimeMillis() - time;

					break;
				} catch (Exception e) {
					log.warn("Jms Operation {} failed, at {}; the exception message : {}", method.getName(), pollingTemplate.getLastOperationDistinationBrokerUrl(), e.getCause().getMessage());
					excludeBroker();
				}
			}

			/**
			 * 检查执行所花的时间，如果耗费的时间过长，则将该template放入到slowBrokersMap中，暂时屏蔽掉。
			 */
			if (sucess == true) {
				AtomicInteger c = slowCountMap.get(pollingTemplate.getLastlyBrokerUrl());
				if (c == null) {
					c = new AtomicInteger();
					slowCountMap.put(pollingTemplate.getLastlyBrokerUrl(), c);
				}
				if (time > slowlyTime) {
					log.warn("the broker [{}] is slowly, spend time is {} millisecond, already had times [{}]", pollingTemplate.getLastlyBrokerUrl(), time, c.get());
					if (c.incrementAndGet() >= slowCount) {
						log.warn("the broker [{}] is slowly, spend time is {} millisecond, it will be exclude, totle [{}] times greater than slowlycount {}", pollingTemplate.getLastlyBrokerUrl(),
								time, c.get(), slowCount);
						c.set(0);
						excludeSlowBroker();
					}
				} else {
					c.set(0);
				}
			}
			/**
			 * 如果 JmsTemplatelist中無可用的JmsTemplate,
			 * 则先尝试用slowBroker中的JmsTemplates进行发送。
			 */
			if (sucess == false) {
				log.warn("try to use slowbrokers jmsTemplates");
				for (Entry<Integer, JmsTemplate> en : slowBrokers.entrySet()) {
					JmsTemplate template = en.getValue();
					if (template != null) {
						try {
							object = method.invoke(template, args);
							sucess = true;
							break;
						} catch (Exception e) {
							log.debug("In slowBrokers operation faild, at [{}] in JmsTemplateList.", en.getKey(), e);
						}
					}
				}
			}

			/**
			 * 如果前面的操作都失败了，则尝试使用UnavailableBrokers中的JmsTemplate
			 */
			if (sucess == false) {
				log.warn("try to use temporary unavailabe jmsTemplates");
				for (Entry<Integer, JmsTemplate> en : UnavailableBrokers.entrySet()) {
					JmsTemplate template = en.getValue();
					if (template != null) {
						try {
							object = method.invoke(template, args);
							sucess = true;
							break;
						} catch (Exception e) {
							log.debug("In unavailable brokers,  operation faild at [{}] in JmsTemplateList.", en.getKey(), e);
						}
					}
				}
			}

			/**
			 * 如果前面的错误都失败，则尝试对所有的JmsTemplate进行操作
			 */
			if (sucess == false) {
				logger.warn("try to use template in allTemplateMap");
				for (Entry<Integer, JmsTemplate> en : pollingTemplate.getAllTemplateMap().entrySet()) {
					JmsTemplate template = en.getValue();
					if (template != null) {
						try {
							object = method.invoke(template, args);
							sucess = true;
							break;
						} catch (Exception e) {
							log.debug("In allTemplateMap,  operation faild at [{}] in JmsTemplateList.", en.getKey(), e);
						}
					}
				}
			}

			if (sucess) {
				return object;
			} else {
				log.error("all brokers are tryed to opertaion {}, but still failed", method.getName());
				throw new UncategorizedJmsException("Maybe all brokers is unaviliable at this time. the method [ " + method.getName() + "] failed");
			}
		}
	}

	/**
	 * 得到pollingTemplate的代理对象，实现 JmsOperations接口
	 */
	public Object getObject() throws Exception {
		assert (pollingTemplate != null);
		tryTimes = pollingTemplate.getBrokerURLs().size();
		assert (tryTimes > 0);
		logger.debug("to create proxy object that implements JmsOperations.class");
		return Proxy.newProxyInstance(this.getClass().getClassLoader(), new Class[] { JmsOperations.class }, new ProxyHandler());
	}

	/**
	 * 排除有问题的broker的JmsTemplate
	 */
	private void excludeBroker() {
		int k = pollingTemplate.getLastlyBrokerIndex();
		if (k >= 0) {
			// 多线程执行时，
			// issueTemplate=pollingTemplate.getJmsTemplateList().get(k)
			// 可能为空值，放到ConcurentHashMap.put方法的value不能为空，否则抛出空指针异常
			JmsTemplate issueTemplate = pollingTemplate.getJmsTemplateList().get(k);
			if (issueTemplate != null) {
				logger.warn("the issueTemplate is not null at [{}] in jmsTemplatelist , it will put to UnavailableBrokersMap", k);
				pollingTemplate.getJmsTemplateList().set(k, null);
				// ConcurreyHashMap的value值不能为空，否则会抛出空指针异常。
				UnavailableBrokers.put(k, issueTemplate);
				timer.schedule(new RevertTast(), reConnectTime);
				logger.warn("add RevertTask to timer with the issue [{}] jmsTemplate, broker Url [{}]", k, pollingTemplate.getLastlyBrokerUrl());
			} else {
				logger.debug("the issueTemplate is null at [{}] in jmsTemplatelist, don't need to put it to UnavailableBrokersMap", k);
			}
		}
	}

	/**
	 * 暂时排除掉处理速度慢的broker的JmsTemplate
	 */
	private void excludeSlowBroker() {
		int k = pollingTemplate.getLastlyBrokerIndex();
		if (k >= 0) {
			// 多线程执行时，
			// issueTemplate=pollingTemplate.getJmsTemplateList().get(k)
			// 可能为空值，放到ConcurentHashMap.put方法的value不能为空，否则抛出空指针异常
			JmsTemplate slowTemplate = pollingTemplate.getJmsTemplateList().get(k);
			if (slowTemplate != null) {
				logger.warn("put the slowTemplate [{}] into SlowBrokersMap ", k);
				pollingTemplate.getJmsTemplateList().set(k, null);
				// ConcurreyHashMap的value值不能为空，否则会抛出空指针异常。
				slowBrokers.put(k, slowTemplate);
				timer.schedule(new RevertSlowBrokerTask(k), slowBrokerBlockTime);
				logger.warn("add RevertSlowBrokerTask to timer with the slowBroker [{}] jmsTemplate, broker Url [{}]", k, pollingTemplate.getLastlyBrokerUrl());
			} else {
				logger.debug("the slowTemplate is null at [{}] in jmsTemplatelist, don't need to put it to slowBrokerMap", k);
			}
		}
	}

	/**
	 * 代理对象的接口
	 */
	public Class getObjectType() {
		return JmsOperations.class;
	}

	public boolean isSingleton() {
		return false;
	}

	public PollingTemplate getPollingTemplate() {
		return pollingTemplate;
	}

	public void setPollingTemplate(PollingTemplate pollingTemplate) {
		this.pollingTemplate = pollingTemplate;
	}

	@Override
	public void start() throws Exception {
		// TODO Auto-generated method stub

	}

	@Override
	public void stop() throws Exception {
		timer.cancel();
	}

}
