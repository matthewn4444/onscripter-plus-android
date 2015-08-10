package com.onscripter.plus;

import android.os.Bundle;
import android.os.Message;
import android.os.Handler;

import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.params.HttpConnectionParams;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;

import java.util.List;
import java.util.Iterator;
import java.util.Arrays;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.CheckedInputStream;
import java.util.zip.CRC32;

public class DataDownloader extends Thread
{
	private byte[] buf = null;

	public DataDownloader( String zip_dir, String zip_filename, String extract_dir, String version_filename, String url, long file_size, Handler h )
	{
		this.zip_dir = zip_dir;
		this.zip_filename = zip_filename;
		this.extract_dir = extract_dir;
		this.version_filename = version_filename;
		this.url = url;
		this.file_size = file_size;
		handler = h;
		buf = new byte[8192*2];

		this.start();
	}
	
	@Override
	public void run()
	{
		File file = new File(zip_dir);
		try {
			file.mkdirs();
		}
		catch( SecurityException e ){
			sendMessage(-2, 0, "Failed to create root directory: " + e.toString());
			return;
		}

		String zip_path = zip_dir + "/" + zip_filename;

		file = new File(zip_path);
		if (file.exists() == false || file.length() != file_size)
			if (downloadZip(zip_path) != 0) return;

		if (extractZip(zip_path) != 0) return;

		if (file_size == -1){
			file = new File(zip_path);
			try {
				file.delete();
			}
			catch( SecurityException e ){
				sendMessage(-2, 0, "Failed to delete temporary file: " + e.toString());
				return;
			}
		}

		file = new File(extract_dir + "/" + version_filename);
		try {
			file.createNewFile();
		} catch( Exception e ) {
			sendMessage(-2, 0, "Failed to create version file: " + e.toString());
			return;
		};

		sendMessage(-1, 0, null);
	}

	private int downloadZip(String zip_path)
	{
		HttpResponse response = null;
		HttpGet request;
		int retry = 0;

		BufferedOutputStream tmp_out = null;
		long downloaded = 0;
		long totalLen = 0;
		while(true){
			DefaultHttpClient client = null;
			request = new HttpGet(url);
			request.addHeader("Accept", "*/*");
			if (totalLen > 0) request.addHeader("Range", "bytes="+downloaded+"-"+totalLen);
			try {
				client = new DefaultHttpClient();
				client.getParams().setBooleanParameter("http.protocol.handle-redirects", true);
				HttpConnectionParams.setConnectionTimeout(client.getParams(), 5000);
				HttpConnectionParams.setSoTimeout(client.getParams(), 3000);
				response = client.execute(request);
			} catch (org.apache.http.conn.ConnectTimeoutException e) {
				retry++;
				continue;
			} catch (java.net.SocketException e) {
				retry++;
				continue;
			} catch (java.net.SocketTimeoutException e) {
				retry++;
				continue;
			} catch (IOException e) {
				sendMessage(-2, 0, "Timeout or zip file is not found: " + e.toString());
				return -1;
			};

			if (response == null || 
				(response.getStatusLine().getStatusCode() != HttpStatus.SC_OK &&
				response.getStatusLine().getStatusCode() != HttpStatus.SC_PARTIAL_CONTENT)){
				response = null;
				sendMessage(-2, 0, "Timeout or zip file is not found.");
				return -1;
			}

			totalLen = response.getEntity().getContentLength();
			if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK){
				downloaded = 0;
				try {
					if (tmp_out != null){
						tmp_out.flush();
						tmp_out.close();
						tmp_out = null;
					}
					tmp_out = new BufferedOutputStream(new FileOutputStream(zip_path));
				} catch( Exception e ) {
					sendMessage(-2, 0, "Failed to create temporary file: " + e.toString());
					return -1;
				};
			}
			else{
				totalLen += downloaded;
			}

			BufferedInputStream stream = null;
			try {
				stream = new BufferedInputStream(response.getEntity().getContent());
			} catch( java.io.IOException e ) {
				client.getConnectionManager().shutdown();
				retry++;
				continue;
			} catch( java.lang.IllegalStateException e ) {
				client.getConnectionManager().shutdown();
				retry++;
				continue;
			}

			try {
				int len = stream.read(buf);
				while (len >= 0){
					if (len > 0) tmp_out.write(buf, 0, len);
					downloaded += len;
					sendMessage((int)downloaded, (int)totalLen, "Downloading archives from Internet: retry " + retry);
					len = stream.read(buf);
					try{
						Thread.sleep(1);
					} catch (InterruptedException e){
					}
				}
			} catch (java.net.SocketException e) {
			} catch( java.net.SocketTimeoutException e ) {
			} catch( java.io.IOException e ) {
				sendMessage(-2, 0, "Failed to write or download: " + e.toString());
				return -1;
			}

			try {
				stream.close();
				stream = null;
			} catch( java.io.IOException e ) {
			};

			client.getConnectionManager().shutdown();

			if (downloaded == totalLen) break;
			retry++;
		}

		try {
			tmp_out.flush();
			tmp_out.close();
			tmp_out = null;
		} catch( java.io.IOException e ) {
		};

		return 0;
	}

	private int extractZip(String zip_path)
	{
		ZipInputStream zip = null;
		try {
			zip = new ZipInputStream(new BufferedInputStream(new FileInputStream(zip_path)));
		} catch( java.io.FileNotFoundException e ) {
			sendMessage(-2, 0, "Failed to read from zip file: " + e.toString());
			return -1;
		};
			
		int num_file = 0;
		while(true){
			num_file++;
			ZipEntry entry = null;
			try {
				entry = zip.getNextEntry();
			} catch( java.io.IOException e ) {
				sendMessage(-2, 0, "Failed to get entry from zip file: " + e.toString());
				return -1;
			}
			if (entry == null) break;

			String path = extract_dir + "/" + entry.getName();
			if (entry.isDirectory()){
				try {
					(new File( path )).mkdirs();
				} catch( SecurityException e ) {
					sendMessage(-2, 0, "Failed to create directory: " + e.toString());
					return -1;
				}
				continue;
			}

			try {
				(new File( path.substring(0, path.lastIndexOf("/") ))).mkdirs();
			} catch( SecurityException e ){
				sendMessage(-2, 0, "Failed to create directory: " + e.toString());
				return -1;
			};

			if (extractZipEntry(path, zip, (int)entry.getSize(), "Extracting archives: " + num_file) != 0) return -1;

			try {
				CheckedInputStream check = new CheckedInputStream( new FileInputStream(path), new CRC32() );
				while( check.read(buf) > 0 ) {};
				check.close();
				if (check.getChecksum().getValue() != entry.getCrc()){
					File ff = new File(path);
					ff.delete();
					throw new Exception();
				}
			} catch( Exception e ){
				sendMessage(-2, 0, "CRC check failed");
				return -1;
			}
		}

		return 0;
	}

	private int extractZipEntry(String out_path, ZipInputStream zip, int total_size, String mes)
	{
		BufferedOutputStream out = null;
		try {
			out = new BufferedOutputStream(new FileOutputStream( out_path ));
		} catch( Exception e ) {
			sendMessage(-2, 0, "Failed to create file: " + e.toString());
			return -1;
		};

		int total_read = 0;
		try {
			int len = zip.read(buf);
			while (len >= 0){
				if (len > 0) out.write(buf, 0, len);
				total_read += len;
				sendMessage(total_read, total_size, mes);
				len = zip.read(buf);
				try{
					Thread.sleep(1);
				} catch (InterruptedException e){
				}
			}
			out.flush();
			out.close();
		} catch( java.io.IOException e ) {
			sendMessage(-2, 0, "Failed to write: " + e.toString());
			return -1;
		}

		return 0;
	}
        
	public void sendMessage(int current, int total, String str){
		Message msg = handler.obtainMessage();
		Bundle b = new Bundle();
		b.putInt("total", total);
		b.putInt("current", current);
		b.putString("message", str);
		msg.setData(b);
		handler.sendMessage(msg);
	}

	private String zip_dir = null;
	private String zip_filename = null;
	private String extract_dir = null;
	private String version_filename = null;
	private String url = null;
	private long file_size = -1;
	private Handler handler = null;
}
