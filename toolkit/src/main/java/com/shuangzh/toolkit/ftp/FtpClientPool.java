package com.shuangzh.toolkit.ftp;

import java.io.IOException;

import org.apache.commons.net.ftp.FTPClient;

public interface FtpClientPool {
	/**
	 * 从资源池中获取FTPClient对象， FTPClient对象已完成连接服务器、登录、设置参数。
	 * @return	FTPClient apache FTPClient 对象
	 * @throws IOException		Ftp服务器连接失败，登录失败等异常
	 */
	FTPClient obtain() throws IOException;
	/**
	 * 回收FTPClient资源
	 * @param client	使用完毕，需要回收的FTPClient
	 */
	void revert(FTPClient client);
	
	/**
	 * 初始化资源池，并进行ftp连接，登录验证。
	 * @throws IOException	Ftp服务器连接失败，登录失败等异常
	 */
	void init() throws IOException;
}
