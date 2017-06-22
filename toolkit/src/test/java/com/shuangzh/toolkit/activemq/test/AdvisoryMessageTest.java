package com.shuangzh.toolkit.activemq.test;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;
import javax.jms.Topic;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.advisory.AdvisorySupport;
import org.apache.activemq.command.ActiveMQMessage;
import org.apache.activemq.command.DestinationInfo;
import org.apache.activemq.command.ProducerInfo;

public class AdvisoryMessageTest {

	public static void main(String[] args) throws JMSException {
		String url="tcp://127.0.0.1:61616";
		ActiveMQConnectionFactory factory= new  ActiveMQConnectionFactory(url);
		
		Connection conn=factory.createConnection();
		
		Session session= conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
		
		Topic topic = session.createTopic("ActiveMQ.Advisory.Queue");
		MessageConsumer consumer= session.createConsumer(topic);
		consumer.setMessageListener(new MessageListener(){

			@Override
			public void onMessage(Message msg) {
				
				System.out.println("consummer receive message");
				
				if (msg instanceof ActiveMQMessage){
			        ActiveMQMessage aMsg =  (ActiveMQMessage)msg;
			        DestinationInfo prod = (DestinationInfo) aMsg.getDataStructure();
			        System.out.println(prod.getOperationType());
			        System.out.println(prod.getDestination().getPhysicalName());
			        System.out.print(prod.isAddOperation());
			    }
			}
			
		});
		conn.start();
		

	}

}
