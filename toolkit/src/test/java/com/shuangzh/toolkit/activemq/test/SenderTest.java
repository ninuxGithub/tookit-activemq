package com.shuangzh.toolkit.activemq.test;

import java.util.ArrayList;
import java.util.List;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.springframework.jms.core.MessageCreator;

import com.shuangzh.toolkit.activemq.GroupQueueSender;

public class SenderTest {
	
	
	
	
	
	public static void main(String[] args) throws Exception {

		GroupQueueSender sender = new GroupQueueSender();
		List<String> urls = new ArrayList<String>();
		urls.add("tcp://127.0.0.1:61616?tcpNoDelay=true&jms.useAsyncSend=true");
		urls.add("tcp://127.0.0.1:9000?tcpNoDelay=true&jms.useAsycSend=true");
		urls.add("tcp://127.0.0.1:61616?tcpNoDelay=true&jms.useAsyncSend=true");
		urls.add("tcp://127.0.0.1:8080?tcpNoDelay=true&jms.useAsyncSend=true");
		
		
		
		sender.setGroupQueueName("ChargeQueueGroup.v1");
		sender.setMaxConnections(1);
		sender.setBrokerURLs(urls);
		sender.start();

		final String msg = "test mesg";
		
		long start = System.currentTimeMillis();
		for (int i = 0; i < 1; i++) {
			try{
			sender.send(new MessageCreator() {

				@Override
				public Message createMessage(Session session) throws JMSException {
					TextMessage message = session.createTextMessage();
					message.setText(msg);
					return message;
				}

			});
			
			}catch (Exception e)
			{
				e.printStackTrace();
			}
			
			
			sender.send("100", new MessageCreator() {

				@Override
				public Message createMessage(Session session)
						throws JMSException {
					TextMessage message = session.createTextMessage();
					message.setText(msg);
					return message;
				}

			});
			
			
			sender.send("200", new MessageCreator() {

				@Override
				public Message createMessage(Session session)
						throws JMSException {
					TextMessage message = session.createTextMessage();
					message.setText(msg);
					return message;
				}
			});
			
			sender.send("300", new MessageCreator() {

				@Override
				public Message createMessage(Session session)
						throws JMSException {
					TextMessage message = session.createTextMessage();
					message.setText(msg);
					return message;
				}
			});
			
			sender.send("400", new MessageCreator() {

				@Override
				public Message createMessage(Session session)
						throws JMSException {
					TextMessage message = session.createTextMessage();
					message.setText(msg);
					return message;
				}
			});
			
			sender.send("500", new MessageCreator() {

				@Override
				public Message createMessage(Session session)
						throws JMSException {
					TextMessage message = session.createTextMessage();
					message.setText(msg);
					return message;
				}
			});
			
			sender.send("600", new MessageCreator() {

				@Override
				public Message createMessage(Session session)
						throws JMSException {
					TextMessage message = session.createTextMessage();
					message.setText(msg);
					return message;
				}
			});
			
			
			sender.send("700", new MessageCreator() {

				@Override
				public Message createMessage(Session session)
						throws JMSException {
					TextMessage message = session.createTextMessage();
					message.setText(msg);
					return message;
				}
			});
		}
		
		long end = System.currentTimeMillis();
		
		System.out.println("=====>time："+ (end - start));
		sender.stop();

	}

}
