package com.group44.tarecruit.data;

import com.group44.tarecruit.model.ActivityLogItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ActivityLogRepositoryTest {
    @TempDir
    Path tempDir;

    @Test
    void keepsOnlyLatestThirtyLogs() {
        ActivityLogRepository repository = new ActivityLogRepository(tempDir.resolve("activity_logs.csv"));
        List<ActivityLogItem> logs = new ArrayList<>();
        for (int index = 1; index <= 35; index++) {
            logs.add(new ActivityLogItem(
                    "log-" + index,
                    "Notification",
                    "admin",
                    "ta-" + index,
                    "Log " + index,
                    "Message " + index,
                    "2026-05-23T10:%02d:00".formatted(index)
            ));
        }

        repository.saveAll(logs);

        List<ActivityLogItem> retainedLogs = repository.findAll();
        assertEquals(30, retainedLogs.size());
        assertEquals("log-35", retainedLogs.getFirst().id());
        assertFalse(retainedLogs.stream().anyMatch(log -> log.id().equals("log-1")));
    }
}
