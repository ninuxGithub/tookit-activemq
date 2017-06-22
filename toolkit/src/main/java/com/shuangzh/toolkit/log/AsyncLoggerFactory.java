package com.shuangzh.toolkit.log;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AsyncLoggerFactory 异步日志工厂类。 通过AsyncLoggerFactory。getLogger获取异步Logger对象，
 * 
 * @author shuangzh
 *
 */
public class AsyncLoggerFactory {

	private static boolean useAsync = false;

	private static boolean useRegion = false;

	private static int LoggingThreadNum = 1;

	private static BlockingQueue<LoggingItem> queue = new LinkedBlockingDeque<LoggingItem>();

	private static int regionNum = 4;

	private static Map<Long, BlockingQueue<LoggingItem>> queueMap = new HashMap<Long, BlockingQueue<LoggingItem>>();

	private static ExecutorService executor = null;

	static {

		if (useRegion) {
			executor = Executors.newFixedThreadPool(LoggingThreadNum * regionNum);
			for (long r = 0; r < regionNum; r++) {
				final BlockingQueue<LoggingItem> newQueue = new LinkedBlockingDeque<LoggingItem>();
				queueMap.put(r, newQueue);
				for (int i = 0; i < LoggingThreadNum; i++) {
					executor.execute(new Runnable() {
						@Override
						public void run() {
							// System.out.println("Async logging thread working :"
							// +
							// Thread.currentThread().getName() + " " +
							// Thread.currentThread().getId());
							for (;;) {
								try {
									// System.out.println("Async logging thread logging:"
									// + Thread.currentThread().getName() + " "
									// +
									// Thread.currentThread().getId());
									newQueue.take().logging();

								} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | InterruptedException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
							}
						}
					});
				}
			}

		} else {
			executor = Executors.newFixedThreadPool(LoggingThreadNum);
			for (int i = 0; i < LoggingThreadNum; i++) {

				executor.execute(new Runnable() {

					@Override
					public void run() {
						// System.out.println("Async logging thread working :" +
						// Thread.currentThread().getName() + " " +
						// Thread.currentThread().getId());
						for (;;) {
							try {
								// System.out.println("Async logging thread logging:"
								// + Thread.currentThread().getName() + " " +
								// Thread.currentThread().getId());
								queue.take().logging();

							} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | InterruptedException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
					}
				});
			}
		}
	}

	public static Logger getLogger(Class<?> clazz) {

		Logger origin = LoggerFactory.getLogger(clazz);

		return (Logger) Proxy.newProxyInstance(AsyncLoggerFactory.class.getClassLoader(), new Class[] { Logger.class }, new LoggerProxy(origin));
	}

	static class LoggingItem {

		Logger origin;
		Method method;
		Object[] args;

		Object logging() throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
			return method.invoke(origin, args);
		}
	}

	static class LoggerProxy implements InvocationHandler {

		Logger origin;

		LoggerProxy(Logger logger) {
			this.origin = logger;
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

			LoggingItem item = new LoggingItem();
			item.origin = origin;
			item.method = method;
			item.args = args;

			if (useAsync) {
				if (useRegion) {
					queueMap.get(Thread.currentThread().getId() % regionNum).offer(item);
				} else {
					queue.offer(item);
				}
			} else {
				return item.logging();
			}
			return null;
		}
	}

	public static void main(String[] args) throws InterruptedException {

		Logger logger = AsyncLoggerFactory.getLogger(AsyncLoggerFactory.class);

		long t = 0;
		long s = 0;
		long e = 0;
		long c=0;
		for (;;) {
			c++;
			s = System.nanoTime();
			Thread.sleep(1);
			logger.info("haha");
			logger.debug("OK");
			e = System.nanoTime();
			t = t + (e - s);
			if(c%10000==0)
			{
				System.out.println("c="+ c/10000 +" ,t="+ t/1000);
			}
		}

	}

}
