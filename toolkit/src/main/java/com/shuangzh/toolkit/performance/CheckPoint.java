package com.shuangzh.toolkit.performance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 用于记录程序运行时间。计算程序运行的两个节点之间的时间差
 * @author shuangzh
 *
 */
public class CheckPoint {

	private static final Logger logger = LoggerFactory.getLogger(CheckPoint.class);

	private static int interval = 500;

	private static ThreadLocal<String> lastPoint = new ThreadLocal<String>();

	private static ThreadLocal<Long> lastStamp = new ThreadLocal<Long>() {
		public Long initialValue() {
			return new Long(0);
		}
	};

	private static ThreadLocal<Long> total = new ThreadLocal<Long>() {

		public Long initialValue() {
			return new Long(0);
		}
	};

	private static ThreadLocal<Map<String, Phase>> phaseMap = new ThreadLocal<Map<String, Phase>>() {

		public Map<String, Phase> initialValue() {
			return new HashMap<String, Phase>();
		}

	};

	private static ThreadLocal<List<Phase>> phaseList = new ThreadLocal<List<Phase>>() {
		public List<Phase> initialValue() {
			return new ArrayList<Phase>();
		};
	};

	public static void mark(String pointname, boolean isStartPoint) {
		mark(pointname);

		if (isStartPoint) {
			Long t = total.get();
			t = t + 1;
			total.set(t);
		}
	}

	public static void mark(String pointname) {

		String last = lastPoint.get();

		long l = System.nanoTime();
		if (last == null) {
			lastPoint.set(pointname);
			lastStamp.set(l);
			return;
		}

		assert (lastStamp.get().longValue() != 0 );
		assert (lastStamp.get().longValue() > 0);
		assert (last !=null);

		long t = l - lastStamp.get();
		
		assert (t > 0);
		
		lastPoint.set(pointname);
		lastStamp.set(l);

		String pname = last + "->" + pointname;

		Phase p = phaseMap.get().get(pname);
		if (p == null) {
			p = new Phase(pname);
			phaseMap.get().put(pname, p);
			phaseList.get().add(p);
		}
		p.addup(t);
	}

	public static void print() {

		if (total.get() % interval == 0) {
			
			StringBuilder sb = new StringBuilder();
			sb.append("Thread: " + Thread.currentThread().getName() + " , id:" + Thread.currentThread().getId() + " " + System.currentTimeMillis()).append("\r\n");
			for (Phase p : phaseList.get()) {
				sb.append("Phase:" + p.name + ",\t t-time:" + p.time + "\t t-count:" + p.count + ",\t per-time:" + (p.time / p.count)).append("\r\n");
			}
			logger.info(sb.toString());
		}
	}

	public static class Phase {

		Phase(String n) {
			this.name = n;
		}

		void addup(long t) {
			this.time += t;
			count++;
		}

		String name;
		long time = 0;
		long count = 0;
	}

	public static void main(String[] args) {

		Thread t = new Thread(new Runnable() {

			@Override
			public void run() {

				for(int k=0; k<=1000000; k++) {

					CheckPoint.mark("start", true);
					for (int i = 0; i < 100000; i++) {
						;
					}
					CheckPoint.mark("point-1");

					for (int i = 0; i < 100000; i++) {
						;
					}

					CheckPoint.mark("point-2");

					for (int i = 0; i < 1000000; i++) {
						;
					}

					CheckPoint.mark("point-3");
					CheckPoint.print();

				}
			}

		});
		t.start();
	}

}
