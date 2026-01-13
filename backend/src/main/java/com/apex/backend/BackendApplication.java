package com.apex.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;

@SpringBootApplication
@EnableScheduling
public class BackendApplication {
	public static void main(String[] args) {
		// #region agent log
		try {
			FileWriter fw = new FileWriter("c:\\Users\\bollu\\github\\Apex-Trading-Bot\\.cursor\\debug.log", true);
			fw.write("{\"id\":\"log_" + System.currentTimeMillis() + "_main\",\"timestamp\":" + Instant.now().toEpochMilli() + ",\"location\":\"BackendApplication.java:12\",\"message\":\"Application startup initiated\",\"data\":{\"argsCount\":\"" + (args != null ? args.length : 0) + "\"},\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"A\"}\n");
			fw.close();
		} catch (IOException e) {}
		// #endregion
		try {
			SpringApplication.run(BackendApplication.class, args);
			// #region agent log
			try {
				FileWriter fw2 = new FileWriter("c:\\Users\\bollu\\github\\Apex-Trading-Bot\\.cursor\\debug.log", true);
				fw2.write("{\"id\":\"log_" + System.currentTimeMillis() + "_main_success\",\"timestamp\":" + Instant.now().toEpochMilli() + ",\"location\":\"BackendApplication.java:25\",\"message\":\"SpringApplication.run completed successfully\",\"data\":{},\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"A\"}\n");
				fw2.close();
			} catch (IOException e) {}
			// #endregion
		} catch (Exception e) {
			// #region agent log
			try {
				FileWriter fw3 = new FileWriter("c:\\Users\\bollu\\github\\Apex-Trading-Bot\\.cursor\\debug.log", true);
				fw3.write("{\"id\":\"log_" + System.currentTimeMillis() + "_main_error\",\"timestamp\":" + Instant.now().toEpochMilli() + ",\"location\":\"BackendApplication.java:30\",\"message\":\"Application startup failed\",\"data\":{\"error\":\"" + (e.getMessage() != null ? e.getMessage().replace("\"", "\\\"") : "null") + "\",\"errorType\":\"" + e.getClass().getName() + "\"},\"sessionId\":\"debug-session\",\"runId\":\"run1\",\"hypothesisId\":\"A\"}\n");
				fw3.close();
			} catch (IOException ex) {}
			// #endregion
			throw e;
		}
	}
}
