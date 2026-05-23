package com.group44.tarecruit.data;

import com.group44.tarecruit.model.ActivityLogItem;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ActivityLogRepository {
    private static final int MAX_LOG_ITEMS = 30;
    private static final List<String> HEADER = List.of(
            "id",
            "category",
            "actorUserId",
            "targetUserId",
            "title",
            "message",
            "createdAt"
    );

    private final Path filePath;

    public ActivityLogRepository(Path filePath) {
        this.filePath = filePath;
    }

    public List<ActivityLogItem> findAll() {
        List<List<String>> rows = CsvUtils.read(filePath);
        if (rows.isEmpty()) {
            return List.of();
        }
        List<ActivityLogItem> logs = new ArrayList<>();
        for (int index = 1; index < rows.size(); index++) {
            List<String> row = rows.get(index);
            logs.add(new ActivityLogItem(
                    valueAt(row, 0),
                    valueAt(row, 1),
                    valueAt(row, 2),
                    valueAt(row, 3),
                    valueAt(row, 4),
                    valueAt(row, 5),
                    valueAt(row, 6)
            ));
        }
        return logs;
    }

    public void append(ActivityLogItem logItem) {
        List<ActivityLogItem> updated = new ArrayList<>(findAll());
        updated.add(logItem);
        saveAll(updated);
    }

    public void saveAll(List<ActivityLogItem> logItems) {
        List<ActivityLogItem> retainedLogItems = logItems.stream()
                .sorted((left, right) -> right.createdAt().compareTo(left.createdAt()))
                .limit(MAX_LOG_ITEMS)
                .toList();
        List<List<String>> rows = retainedLogItems.stream()
                .map(item -> List.of(
                        item.id(),
                        item.category(),
                        item.actorUserId(),
                        item.targetUserId(),
                        item.title(),
                        item.message(),
                        item.createdAt()
                ))
                .toList();
        CsvUtils.write(filePath, HEADER, rows);
    }

    private String valueAt(List<String> row, int index) {
        return index < row.size() ? row.get(index) : "";
    }
}
