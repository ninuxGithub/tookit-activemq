package com.shuangzh.toolkit.ftp.impl;

import java.io.IOException;
import java.util.Comparator;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.shuangzh.toolkit.ftp.FtpClientPool;



public class FtpClientPoolImpl implements FtpClientPool {

	private static final Logger logger = LoggerFactory.getLogger(FtpClientPool.class);

	private volatile AtomicInteger current = new AtomicInteger(); // 记录已生成的资源数量，生成的资源数量不能超过
																	// poolsize
	private PriorityBlockingQueue<FtpClientStamp> queue = null; // 资源阻塞队列

	private int poolsize = 20; // ftp client
								// 资源池的大小，即最大能启动的ftp连接数，如果已达到最大值，则会阻塞，等待其他线程释放资源
	private int threshold = 2; // 资源池空闲ftp连接阈值，当资源池中的空闲连接大于此值时，
								// 资源池再回收ftp连接时，将会断开连接。
	private String hostname; // ftp服务器主机名，ip地址
	private String username; // ftp 用户名
	private String password; // ftp 用户密码
	private int port; // ftp 端口号

	private int fileType = FTP.BINARY_FILE_TYPE; // ftp 文件模式
	private int fileTransferMode = FTP.STREAM_TRANSFER_MODE; // ftp 文件传输模式
	private boolean localActiveMode; // 主被动模式设置
	private boolean localPassiveMode;
	private boolean remotePassiveMode;

	private boolean iflag = false;

	public boolean isRemotePassiveMode() {
		return remotePassiveMode;
	}

	public void setRemotePassiveMode(boolean remotePassiveMode) {
		this.remotePassiveMode = remotePassiveMode;
	}

	public int getFileTransferMode() {
		return fileTransferMode;
	}

	public void setFileTransferMode(int fileTransferMode) {
		this.fileTransferMode = fileTransferMode;
	}

	public int getFileType() {
		return fileType;
	}

	public void setFileType(int fileType) {
		this.fileType = fileType;
	}

	public boolean isLocalActiveMode() {
		return localActiveMode;
	}

	public void setLocalActiveMode(boolean localActiveMode) {
		this.localActiveMode = localActiveMode;
	}

	public boolean isLocalPassiveMode() {
		return localPassiveMode;
	}

	public void setLocalPassiveMode(boolean localPassiveMode) {
		this.localPassiveMode = localPassiveMode;
	}

	public int getPoolsize() {
		return poolsize;
	}

	public void setPoolsize(int poolsize) {
		this.poolsize = poolsize;
	}

	public int getThreshold() {
		return threshold;
	}

	public void setThreshold(int threshold) {
		this.threshold = threshold;
	}

	public String getHostname() {
		return hostname;
	}

	public void setHostname(String hostname) {
		this.hostname = hostname;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	/**
	 * 获取FTPClient对象， 如果queue的长度为0，并且已生成的总数量
	 */
	@Override
	public FTPClient obtain() throws IOException {
		FTPClient client = null;
		if (queue.size() == 0 && current.get() + queue.size() < poolsize) {
			client = this.genFtpClient();
			
			current.incrementAndGet();
			logger.debug("obtain -- current {} ftp client in using, {} ftp client in queue", current.get(), queue.size());
			return client;
		}

		try {
			logger.debug("to obtain ftp client from queue");

			FtpClientStamp clientstamp = queue.take();
			client = clientstamp.getClient();

			logger.debug("obtain ftpclientstamp {} ,ftpclient {} , stamp {}", clientstamp, client, clientstamp.getStamp());

			if (client.isConnected()) {
				try {
					boolean b = client.sendNoOp();
					logger.debug("sendNoOp() ReplyCode {}", client.getReplyCode());
					if (FTPReply.isPositiveCompletion(client.getReplyCode())) {
						
						current.incrementAndGet();
						logger.debug("obtain -- current {} ftp client in using, {} ftp client in queue", current.get(), queue.size());
						return client;
					} else {
						client.disconnect();
					}
				} catch (IOException e) {
					logger.warn("sendNoOp exception happened, the ftpclient may be can't use any more, new ftpclient will be created");
					logger.warn("ftp client sendNoOp exception message: {}", e.getMessage());
					e.printStackTrace();
				}
			}

			client = this.genFtpClient();
			
			current.incrementAndGet();
			logger.debug("obtain --- current {} ftp client in using, {} ftp client in queue", current.get(), queue.size());
			return client;
			
		} catch (InterruptedException e) {
			logger.debug("obtain ftp client blocking is interrupted, ftp client with null value will be return");
			e.printStackTrace();
			throw new IOException("obtain ftp client blocking interrupted", e);
		}
	}

	@Override
	public void revert(FTPClient client) {
		if (client != null) {
			
			current.decrementAndGet();
			logger.debug("revert --- current {} ftp client in using, {} ftp client in queue", current.get(), queue.size());
			
			if (queue.size() >= threshold) {
				if (client.isConnected()) {
					try {
						logger.debug("queue size gt threshold, the client will disconnect");
						client.disconnect();
					} catch (IOException e) {
						logger.warn("when revert ftpclient, client disconnect exception happened");
						e.printStackTrace();
					}
				}
				this.queue.put(new FtpClientStamp(client, 0));
			} else {

				try {
					logger.debug("revert ftpclient, current path {}", client.printWorkingDirectory());
				} catch (IOException e) {
					e.printStackTrace();
				}

				try {
					boolean b = client.changeWorkingDirectory("/");
					if (b == false) {
						logger.warn("ftpclient change to root dir failed, replay code {}", client.getReplyCode());
						client.disconnect();
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
				this.queue.put(new FtpClientStamp(client));
			}
		}
		logger.debug("after revert  --- current {} ftp client in using, {} ftp client in queue", current.get(), queue.size());
	}

	/**
	 * 初始化资源池
	 */
	@Override
	public void init() throws IOException {
		logger.info("ftpclient pool inintial...");
		if (iflag)
			return;

		// 根据时间排序，时间值越大的，排在队列最前面。
		Comparator<FtpClientStamp> OrderIsTime = new Comparator<FtpClientStamp>() {
			@Override
			public int compare(FtpClientStamp o1, FtpClientStamp o2) {
				return o1.getStamp() > o2.getStamp() ? -1 : 1;
			}
		};
		queue = new PriorityBlockingQueue<FtpClientStamp>(11, OrderIsTime);

		iflag = true;
		FTPClient client = null;
		client = this.obtain();
		logger.info("ftp client pool initailed success");
		this.revert(client);
	}

	/**
	 * 生成新的FTPClient对象，完成连接服务器，登录，设置参数。
	 * 
	 * @return
	 * @throws IOException
	 */
	private FTPClient genFtpClient() throws IOException {
		FTPClient f = new FTPClient();
		boolean b = false;
		int rcode;

		try {
			f.connect(hostname, port);
		} catch (IOException e) {
			logger.error("ftpclient can not connect to host {} port {}", hostname, port);
			throw new IOException("ftpclient can not connect to host " + hostname + " port " + port, e);
		}

		b = f.login(username, password);
		if (b == false) {
			logger.error("ftp login on server {} failed with name {}, pwd {}", hostname, username, password);
			throw new IOException("ftp login failed with name " + username + ", pwd  " + password);
		}
		rcode = f.getReplyCode();
		if (FTPReply.isPositiveCompletion(rcode)) {
			logger.debug("new ftp client login success on ftp server {}, ftp client object{}", hostname, f);

			f.setFileType(this.fileType);
			f.setFileTransferMode(this.fileTransferMode);
			if (this.localActiveMode) {
				f.enterLocalActiveMode();
			}
			if (this.localPassiveMode) {
				f.enterLocalPassiveMode();
			}

			if (this.remotePassiveMode) {
				f.enterRemotePassiveMode();
			}

			return f;
		} else {
			logger.error("new ftp client login ReplayCode {}", rcode);
			f.disconnect();
			throw new IOException("ftp login success, but the replycode " + rcode);
		}
	}

	/**
	 * 内部类， 用于在FTPClient资源上增加个时间标记，在优先阻塞队列中使用，时间标记值越大的，放在队列最前面。
	 * 
	 * @author hpcmsz
	 *
	 */
	private class FtpClientStamp {

		FtpClientStamp(FTPClient client) {
			this.client = client;
			this.stamp = System.currentTimeMillis();
		}

		FtpClientStamp(FTPClient client, int timestamp) {
			this.client = client;
			this.stamp = timestamp;
		}

		private FTPClient client;
		private long stamp;

		public FTPClient getClient() {
			return client;
		}

		public long getStamp() {
			return stamp;
		}

		public void setStamp(long stamp) {
			this.stamp = stamp;
		}
	}

	public static void main(String[] args) throws IOException {
		FtpClientPoolImpl pool = new FtpClientPoolImpl();
		pool.setHostname("192.168.0.211");
		pool.setPort(4433);
		pool.setUsername("upay3clearing");
		pool.setPassword("upay3clearing");
		pool.setPoolsize(2);
		pool.setThreshold(2);

		pool.init();
		FTPClient c1 = pool.obtain();
		FTPClient c2 = pool.obtain();
//		pool.revert(c1);
//		pool.revert(c2);
		FTPClient c3 = pool.obtain();
		FTPClient c4 = pool.obtain();
	}
}
