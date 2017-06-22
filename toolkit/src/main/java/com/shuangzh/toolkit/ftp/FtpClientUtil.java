package com.shuangzh.toolkit.ftp;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.net.ftp.FTPFile;

public interface FtpClientUtil {
	
	/**
	 * 上传文件到ftp服务器
	 * @param local		本地输入流，即需要传输到服务器的数据
	 * @param path		ftp 服务器目录
	 * @param name		上传后，在ftp服务器上的文件名
	 * @param suffix	文件上传时添加的文件后缀，上传完成时，重名为name; suffix 为null 时，则不加后缀
	 * @throws IOException	连接不到ftp服务器，登录失败，文件上传失败等异常
	 */
	void put(InputStream local, String path, String name, String suffix) throws IOException;
	
	/**
	 * 上传文件到ftp服务器
	 * @param local		本地 文件，File类型参数
	 * @param path		ftp	服务器目录
	 * @param name		上传后，在ftp服务器上的文件名
	 * @param suffix	文件上传时添加的文件后缀，上传完成时，重名为name; suffix 为null 时，则不加后缀
	 * @throws IOException	连接不到ftp服务器，登录失败，文件上传失败等异常
	 */
	void put(File local, String path, String name, String suffix) throws IOException;
	
	/**
	 * 上传文件到ftp服务器
	 * @param local		本地 文件路径
	 * @param path		ftp	服务器目录
	 * @param name		上传后，在ftp服务器上的文件名
	 * @param suffix	文件上传时添加的文件后缀，上传完成时，重名为name; suffix 为null 时，则不加后缀
	 * @throws IOException	连接不到ftp服务器，登录失败，文件上传失败等异常
	 */
	void put(String local, String path, String name, String suffix) throws IOException;
	
	/**
	 * 从ftp服务器下载文件
	 * @param local		本地保存数据的输出流， 即下载的数据将写入该输出流
	 * @param path		ftp 服务器目录
	 * @param name		要下载的文件名
	 * @throws IOException
	 */
	void get(OutputStream local, String path, String name) throws IOException;
	
	/**
	 * 从ftp服务器下载文件
	 * @param local		本地文件， File类型参数
	 * @param path		ftp 服务器目录
	 * @param name		要下载的文件名
	 * @throws IOException
	 */
	void get(File local, String path, String name)  throws IOException;
	
	/**
	 * 从ftp服务器下载文件, 下载后的文件名与ftp服务器上的文件名一致
	 * @param localDir	本地路径
	 * @param path		ftp 服务器目录
	 * @param name		要下载的文件名
	 * @throws IOException
	 */
	void get(String localDir, String path, String name) throws IOException;
	
	/**
	 * 从ftp服务器下载文件,下载后的文件名与ftp服务器上的文件名一致
	 * @param localDir
	 * @param pathname	ftp服务器上文件的完整路径名（包括文件名）
	 * @throws IOException
	 */
	void get(String localDir, String pathname) throws IOException;
	
	
	/**
	 * 获取ftp服务目录下文件或目录名,
	 * @param pathname	ftp服务器路径
	 * @return	注意返回的路径是全路径名
	 * @throws IOException 
	 */
	String[] listNames(String pathname) throws IOException;
	
	/**
	 * 获取ftp服务器目录下的文件或目录
	 * @param pathname	ftp服务器路径
	 * @return	返回 FTPFile对象， FTPFile对象可能是文件或目录， 通过FTPFile.isFile或FTPFile.isDirectory判断
	 * 			获取单独的文件通过FTPFile.getName()函数获取
	 */
	FTPFile[] listFiles(String pathname) throws IOException;
	
	/**
	 * 刪除ftp服務器上的文件
	 * @param pathname	文件在ftp服務器上的全路徑（包括文件名）
	 * @return	true 成功刪除， false 刪除失敗。
	 * @throws IOException
	 */
	boolean deleteFile(String pathname)	throws IOException;
	
}
