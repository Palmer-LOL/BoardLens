package lol.palmer.boardlens;

import android.app.Application;

public final class BoardLensApp extends Application {
    private AppSession session;
    synchronized AppSession session() {
        if (session == null) session = new AppSession(this);
        return session;
    }
}
