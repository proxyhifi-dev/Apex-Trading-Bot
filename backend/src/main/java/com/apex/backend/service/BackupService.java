package com.apex.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@Slf4j
public class BackupService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Run every day at Midnight (00:00)
    @Scheduled(cron = "0 0 0 * * ?")
    public void performDailyBackup() {
        log.info("💾 Starting Daily Database Backup...");
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = "backup_" + timestamp + ".zip";
            String backupPath = "./backups/" + fileName;

            // Create dir if not exists
            new File("./backups").mkdirs();

            // H2 Specific Command to create a backup
            jdbcTemplate.execute("SCRIPT TO '" + backupPath + "' COMPRESSION ZIP");

            log.info("✅ Database Backup Successful: {}", backupPath);
        } catch (Exception e) {
            log.error("❌ Database Backup Failed: {}", e.getMessage());
        }
    }
}