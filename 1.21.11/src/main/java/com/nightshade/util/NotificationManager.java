package com.nightshade.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Queue of toasts. Rendering is performed by the Notifications interface module; when that module is disabled
 * notifications are still logged but not displayed.
 */
public final class NotificationManager {
    private static final int MAX_VISIBLE = 8;
    private final List<Notification> active = new ArrayList<>();

    public synchronized void push(String title, String message, Notification.Type type) {
        Log.info("[{}] {}", title, message);
        for (Notification n : active) {
            if (n.title().equals(title) && n.message().equals(message) && n.type() == type) {
                n.repeat(); // merge duplicates into one toast with a counter
                return;
            }
        }
        active.add(new Notification(title, message, type, type == Notification.Type.ERROR ? 6000L : 3000L));
        while (active.size() > MAX_VISIBLE) {
            active.remove(0);
        }
    }

    public void info(String title, String message) {
        push(title, message, Notification.Type.INFO);
    }

    public void success(String title, String message) {
        push(title, message, Notification.Type.SUCCESS);
    }

    public void warn(String title, String message) {
        push(title, message, Notification.Type.WARNING);
    }

    public void error(String title, String message) {
        push(title, message, Notification.Type.ERROR);
    }

    /** Returns a snapshot of live notifications after pruning expired ones (expired toasts finish sliding out). */
    public synchronized List<Notification> snapshot() {
        long now = System.currentTimeMillis();
        Iterator<Notification> iterator = active.iterator();
        while (iterator.hasNext()) {
            Notification notification = iterator.next();
            if (notification.isExpired(now)) {
                notification.slide().setTarget(0f);
                if (notification.slide().get() <= 0.01f) {
                    iterator.remove();
                }
            }
        }
        return new ArrayList<>(active);
    }

    public synchronized void clear() {
        active.clear();
    }
}
