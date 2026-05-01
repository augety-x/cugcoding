package com.cugcoding.forum.config;

import com.cugcoding.forum.service.ForumService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class StartupRunner implements CommandLineRunner {
    private final ForumService service;

    public StartupRunner(ForumService service) {
        this.service = service;
    }

    @Override
    public void run(String... args) {
        try {
            service.init();
        } catch (Exception e) {
            System.err.println("Forum startup init skipped: " + e.getMessage());
        }
        // Rebuild ES search index after DB is ready
        try {
            service.rebuildIndex();
        } catch (Exception e) {
            System.err.println("ES index rebuild skipped (ES may not be running): " + e.getMessage());
        }
    }
}
