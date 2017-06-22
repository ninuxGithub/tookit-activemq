package com.shuangzh.toolkit.activemq.test;

import java.io.IOException;
import java.util.Iterator;
import java.util.Set;

import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanInfo;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

public class TestJmx {

	public static void main(String[] args) throws IOException, InstanceNotFoundException, IntrospectionException, ReflectionException, MalformedObjectNameException {
		
        String surl = "service:jmx:rmi:///jndi/rmi://localhost:1099/jmxrmi";  
        
        JMXServiceURL url = new JMXServiceURL(surl);  
        JMXConnector jmxc = JMXConnectorFactory.connect(url, null);  
        MBeanServerConnection mbsc = jmxc.getMBeanServerConnection();  
        
        String[] domains= mbsc.getDomains();
        for(String d : domains)
        {
        	System.out.println(d);
        }
        
        ObjectName mbeanName = new ObjectName("org.apache.activemq:type=Broker,brokerName=localhost");
        MBeanInfo info = mbsc.getMBeanInfo(mbeanName);
        
        System.out.println(info.getClassName());
        
        ObjectInstance objins=mbsc.getObjectInstance(mbeanName);
        System.out.println(objins.getObjectName()); 
        
        
        System.out.println("all ObjectName：---------------");
		Set<ObjectInstance> set = mbsc.queryMBeans(null, null);
		for (Iterator<ObjectInstance> it = set.iterator(); it.hasNext();) {
			ObjectInstance oi = (ObjectInstance) it.next();
			System.out.println("\t" + oi.getObjectName());
		}
		
        System.out.println("queue ObjectName：---------------");
        
        
        ObjectName objectselect = new ObjectName("org.apache.activemq:type=Broker,destinationType=Queue,*");
		Set<ObjectInstance> set1 = mbsc.queryMBeans(objectselect, null);
		for (Iterator<ObjectInstance> it = set1.iterator(); it.hasNext();) {
			ObjectInstance oi = (ObjectInstance) it.next();
			System.out.println("\t" + oi.getObjectName());
		}
		
		
		System.out.println("queue ddd  ObjectName：---------------");
		Set<ObjectName> set2 = mbsc.queryNames(objectselect, null);
		for (Iterator<ObjectName> it = set2.iterator(); it.hasNext();) {
			ObjectName oi = (ObjectName) it.next();
			System.out.println("\t" + oi.toString());
			System.out.println("\t" + oi.getKeyProperty("destinationName"));
		}
		
		
	}

}
