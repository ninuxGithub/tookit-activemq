package com.shuangzh.toolkit.ftp.impl;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.shuangzh.toolkit.ftp.FtpClientPool;
import com.shuangzh.toolkit.ftp.FtpClientUtil;


public class FtpClientUtilImpl implements FtpClientUtil {

	private static final Logger logger = LoggerFactory.getLogger(FtpClientUtilImpl.class);

	private FtpClientPool pool; // ftp client 资源池

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

	public int getFileType() {
		return fileType;
	}

	public void setFileType(int fileType) {
		this.fileType = fileType;
	}

	public int getFileTransferMode() {
		return fileTransferMode;
	}

	public void setFileTransferMode(int fileTransferMode) {
		this.fileTransferMode = fileTransferMode;
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

	public boolean isRemotePassiveMode() {
		return remotePassiveMode;
	}

	public void setRemotePassiveMode(boolean remotePassiveMode) {
		this.remotePassiveMode = remotePassiveMode;
	}

	public FtpClientPool getPool() {
		return pool;
	}

	public void setPool(FtpClientPool pool) {
		this.pool = pool;
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
	 * 初始化ftp客户端资源池
	 * 
	 * @throws IOException
	 *             ftp服务器无法连接，或登录失时异常
	 */
	public void init() throws IOException {
		FtpClientPoolImpl p = new FtpClientPoolImpl();
		this.pool = p;
		p.setHostname(hostname);
		p.setPassword(password);
		p.setPoolsize(poolsize);
		p.setPort(port);
		p.setThreshold(threshold);
		p.setUsername(username);

		p.setLocalActiveMode(localActiveMode);
		p.setLocalPassiveMode(localPassiveMode);
		p.setRemotePassiveMode(remotePassiveMode);
		p.setFileType(fileType);
		p.setFileTransferMode(fileTransferMode);

		p.init();
	}

	/**
	 * 上传文件到ftp 服务器。
	 * 
	 * @param local
	 *            本地输入流
	 * @param path
	 *            ftp 服务器目录
	 * @param name
	 *            存储到ftp 服务器的文件名
	 * @param suffix
	 *            文件传输时增加后缀， 传输完成后，重命名为name
	 * @exception IOException
	 *                ftp连接，登录失败，传输失败等异常。
	 */
	@Override
	public void put(InputStream local, String path, String name, String suffix) throws IOException {
		FTPClient ftp = pool.obtain();

		boolean b = this.accessDir(path, ftp);
		if (b == false) {
			logger.error("can't access path {} on ftp serer", path);

			pool.revert(ftp);
			throw new IOException("can't access path " + path + " on ftp server");
		}

		String fname = name;
		if (suffix != null && suffix.length() > 0) {
			fname = name + suffix;
		}
		logger.debug("begin to upload file to ftp sever, dir : {}, file name : {}", path, fname);
		b = ftp.storeFile(fname, local);
		if (b == false) {
			logger.error("store file to ftp server failed, file name : {}, dir:{}", fname, path);

			pool.revert(ftp);
			throw new IOException("upload file, ftp server store file failed fname:" + fname + ", dir:" + path);
		} else {
			if (!ftp.rename(fname, name)) {
				logger.error("rename file, ftp server rename file failed fname:" + fname + ", dir:" + path + ",name:"
						+ name);
				throw new IOException("rename file, ftp server rename file failed fname:" + fname + ", dir:" + path
						+ ",name:" + name);
			}
		}
		logger.debug("upload file to ftp server success, dir:{}, file name:{}", path, fname);

		pool.revert(ftp);
	}

	/**
	 * 从ftp服务器下载文件，并写入到本地 outputStream 中
	 * 
	 * @param local
	 *            本地outputStream, 从服务器上下载的文件将写入此输出流中
	 * @param path
	 *            ftp服务器目录，
	 * @param name
	 *            ftp服务器上的文件名
	 * @exception IOException
	 *                ftp连接，登录失败，传输失败等异常。
	 */
	@Override
	public void get(OutputStream local, String path, String name) throws IOException {
		FTPClient ftp = pool.obtain();

		boolean b = this.accessDir(path, ftp);
		if (b == false) {
			logger.error("can't access path {} on ftp serer", path);
			local.close();
			pool.revert(ftp);
			throw new IOException("can't access path " + path + " on ftp server");
		}

		b = ftp.retrieveFile(name, local);
		if (b == false) {
			logger.error("retrieve file from ftp server failed, fname : {}, dir:{}, reply code :{}", name, path,
					ftp.getReplyCode());
			local.close();
			pool.revert(ftp);
			throw new IOException("ftpclient download file failed form ftp server, fname:" + name + ", dir:" + path
					+ ", replay code:" + ftp.getReplyCode());
		} else {
			logger.debug("download file from ftp server success, path {} , name {}", path, name);
		}
		local.close();
		pool.revert(ftp);
	}

	private boolean accessDir(String path, FTPClient ftp) {
		boolean b;
		try {
			b = ftp.changeWorkingDirectory(path);
			if (b == false) {
				String[] ps = path.split("/");
				String dir = "";
				for (String p : ps) {
					if (StringUtils.isNotBlank(p)) {
						dir += "/" + p;
						b = ftp.makeDirectory(dir);
					}
				}
				if (b == false) {
					logger.error("can't create path {} on ftp server", path);
					return false;
				} else {
					logger.info("mkdir  {} on ftp server, success", path);
				}
				b = ftp.changeWorkingDirectory(path);
			}
		} catch (IOException e) {
			logger.error("can't access path {} on server, exception happened", path);
			e.printStackTrace();
			return false;
		}

		if (b == false) {
			logger.error("can't access path {} on server", path);
		}
		return b;
	}

	/**
	 * 上传文件到ftp服务器。 重载this.put()函数，将InputStream参数变为 File
	 * 
	 * @param local
	 *            File类型，本地文件
	 */
	@Override
	public void put(File local, String path, String name, String suffix) throws IOException {
		BufferedInputStream bin = new BufferedInputStream(new FileInputStream(local));
		this.put(bin, path, name, suffix);
		bin.close();
	}

	/**
	 * 上传文件到ftp服务器。 重载this.put()函数，将InputStream参数变为 String
	 * 
	 * @param String
	 *            local 本地文件路径
	 */

	@Override
	public void put(String local, String path, String name, String suffix) throws IOException {
		BufferedInputStream bin = new BufferedInputStream(new FileInputStream(local));
		this.put(bin, path, name, suffix);
		bin.close();
	}

	/**
	 * 从ftp服务器下载文件 重载 this.get()函数， 将OutputStream参数变为File
	 * 
	 * @param File
	 *            local, 本地文件
	 */

	@Override
	public void get(File local, String path, String name) throws IOException {
		BufferedOutputStream bout = new BufferedOutputStream(new FileOutputStream(local));
		this.get(bout, path, name);
		bout.flush();
		bout.close();
	}

	/**
	 * 从ftp服务器下载文件, 本地文件与服务器文件同名 重载 this.get()函数， 将OutputStream参数变为localDir
	 * 
	 * @param String
	 *            localDir 本地路径
	 */

	@Override
	public void get(String localDir, String path, String name) throws IOException {
		String file = localDir + File.separator + name;
		BufferedOutputStream bout = new BufferedOutputStream(new FileOutputStream(file));
		try {
			this.get(bout, path, name);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			throw e;
		} finally {
			bout.flush();
			bout.close();
		}
	}

	/**
	 * 获取ftp服务目录下文件或目录名,
	 * 
	 * @param pathname
	 *            ftp服务器路径
	 * @return 注意返回的路径是全路径名
	 * @throws IOException
	 */
	@Override
	public String[] listNames(String pathname) throws IOException {
		FTPClient ftp = this.pool.obtain();
		String[] names = null;
		try {
			names = ftp.listNames(pathname);
		} catch (IOException e) {
			logger.error("ftp client listNames exception happend, {}", e.getMessage());
			e.printStackTrace();
			throw e;
		} finally {
			this.pool.revert(ftp);
		}
		return names;
	}

	/**
	 * 获取ftp服务器目录下的文件或目录
	 * 
	 * @param pathname
	 *            ftp服务器路径
	 * @return 返回 FTPFile对象， FTPFile对象可能是文件或目录，
	 *         通过FTPFile.isFile或FTPFile.isDirectory判断
	 *         获取单独的文件通过FTPFile.getName()函数获取
	 */
	@Override
	public FTPFile[] listFiles(String pathname) throws IOException {
		FTPClient ftp = this.pool.obtain();
		FTPFile[] files = null;
		try {
			files = ftp.listFiles(pathname);
		} catch (IOException e) {
			logger.error("ftp client listFiles exception happend, {}", e.getMessage());
			e.printStackTrace();
			throw e;
		} finally {
			this.pool.revert(ftp);
		}
		return files;
	}

	/**
	 * 从ftp服务器下载文件,下载后的文件名与ftp服务器上的文件名一致
	 * 
	 * @param localDir
	 * @param pathname
	 *            ftp服务器上文件的完整路径名（包括文件名）
	 * @throws IOException
	 */
	@Override
	public void get(String localDir, String pathname) throws IOException {
		int l = pathname.lastIndexOf("/");
		String path = pathname.substring(0, l);
		String name = pathname.substring(l + 1);
		try {
			this.get(localDir, path, name);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			throw e;
		}
	}

	@Override
	public boolean deleteFile(String pathname) throws IOException {
		FTPClient ftp = this.pool.obtain();
		boolean b = ftp.deleteFile(pathname);
		this.pool.revert(ftp);
		return b;
	}

	public static void main(String[] args) {
		FtpClientPoolImpl pool = new FtpClientPoolImpl();
		pool.setHostname("192.168.86.65");
		pool.setPort(21);
		pool.setUsername("upay3");
		pool.setPassword("upay3");
		pool.setPoolsize(2);
		pool.setThreshold(1);

		try {
			pool.init();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		FtpClientUtilImpl util = new FtpClientUtilImpl();
//		util.setPool(pool);
		try {
			InputStream in = new FileInputStream("log4j.properties");
			OutputStream out = new FileOutputStream("out.txt");
			try {
				util.put(in, "/tes/haha", "newName123", ".doing");
				util.get(out, "/tes/haha", "newName1");
				in.close();
				out.close();
				String[] names = util.listNames("/test");
				for (String s : names) {
					System.out.println(s);
				}
				FTPFile[] files = util.listFiles("/test");
				for (FTPFile f : files) {
					System.out.println(f.getName());
				}

				util.get(".", "/tes/haha/newName1");

			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

}
