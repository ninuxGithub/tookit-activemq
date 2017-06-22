package com.shuangzh.toolkit.activemq.test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;

import com.shuangzh.toolkit.activemq.GroupQueueMessageListenerContainer;

public class ContainerTest {

	public static void main(String[] args) throws IOException {
		
		GroupQueueMessageListenerContainer container = new GroupQueueMessageListenerContainer();
		
		
		List<String> urls=new ArrayList<String>();
		urls.add("tcp://127.0.0.1:61616");
		
		List<String> jmxurls=new ArrayList<String>();
//		jmxurls.add("service:jmx:rmi:///jndi/rmi://localhost:1099/jmxrmi");
		
		container.setGroupQueueName("ChargeQueueGroup.v1");
//		container.setInterval(30000);
//		container.setBrokerJmxURLs(jmxurls);
		container.setBrokerURLs(urls);
		container.setConcurrentConsumers(2);
		container.setSubQueueConsumerCfg("100:10, 200:4, 600:29, 700:11, default:30");
		
		container.setMessageListener(new MessageListener(){
			@Override
			public void onMessage(Message message) {
				TextMessage msg=(TextMessage)message;
				
				try {
					System.out.println("receive msg " + msg.getText());
				} catch (JMSException e) {
					e.printStackTrace();
				}
			}
		});
		container.start();
		
		System.in.read();
		
		
		

	}

}
