package com.nicemq.node.rest;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;

import org.axe.annotation.ioc.Controller;
import org.axe.annotation.mvc.FilterFuckOff;
import org.axe.annotation.mvc.Request;
import org.axe.constant.CharacterEncoding;
import org.axe.constant.ContentType;
import org.axe.constant.RequestMethod;
import org.axe.exception.RestException;
import org.axe.util.LogUtil;

@Controller(basePath="/log",desc="服务器日志")
public class LogRest {
	@FilterFuckOff
	@Request(path="/",method=RequestMethod.GET,desc="输出服务器日志")
	public void log(HttpServletResponse response){
		BufferedReader reader = null;
		InputStream in = this.getClass().getClassLoader().getResourceAsStream("log.txt");
		if (in == null) {
			throw new RestException(RestException.SC_NOT_FOUND, "");
		}
		try {
			reader = new BufferedReader(new InputStreamReader(in));
			response.setCharacterEncoding(CharacterEncoding.UTF_8.CHARACTER_ENCODING);
			response.setContentType(ContentType.APPLICATION_HTML.CONTENT_TYPE);
			ServletOutputStream out = response.getOutputStream();
			String line = reader.readLine();
			while (line != null) {
				out.write(("<p>"+line + System.lineSeparator()+"</p>").getBytes(CharacterEncoding.UTF_8.CHARACTER_ENCODING));
				line = reader.readLine();
			}
			// writer.flush();
			// writer.close();
		} catch (Exception e) {
			LogUtil.error(e);
		} finally {
			try {
				if (reader != null) {
					reader.close();
				}
			} catch (Exception e) {
			}
		}
	}
	
}
