package com.pongcausa;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Registro de dados: sensores (~cada evento) e estado do jogo (~15ms), como na monografia. */
public class GameLog {
    private final File dir;
    private final SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);
    private BufferedWriter gameWriter, sensorWriter, metaWriter;
    public final String sessionId;
    private long gameRows = 0, sensorRows = 0;

    public GameLog(File baseDir, String sessionId) {
        this.dir = baseDir;
        this.sessionId = sessionId;
    }

    public static File sessionRoot(File base, String id) {
        File f = new File(base, id);
        f.mkdirs();
        return f;
    }

    public void open() throws IOException {
        File root = sessionRoot(dir, sessionId);
        gameWriter = new BufferedWriter(new FileWriter(new File(root, "game.csv")));
        gameWriter.write("t_ms,since_start_ms,ball_x,ball_y,ball_vx,ball_vy,ball_speed,p1_y,p2_y,score_p1,score_p2,event\n");
        sensorWriter = new BufferedWriter(new FileWriter(new File(root, "sensors.csv")));
        sensorWriter.write("t_ms,since_start_ms,sensor,accuracy,values\n");
        metaWriter = new BufferedWriter(new FileWriter(new File(root, "meta.txt")));
    }

    public void meta(String key, String value) {
        try {
            if (metaWriter != null) {
                metaWriter.write(key + "=" + value + "\n");
                metaWriter.flush();
            }
        } catch (IOException ignored) {}
    }

    public void game(long tAbsMs, long sinceStart, float bx, float by, float bvx, float bvy, float bspd,
                     float p1y, float p2y, int s1, int s2, String event) {
        try {
            gameWriter.write(String.format(Locale.US,
                "%d,%d,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%d,%d,%s\n",
                tAbsMs, sinceStart, bx, by, bvx, bvy, bspd, p1y, p2y, s1, s2, event));
            gameRows++;
            if (gameRows % 256 == 0) gameWriter.flush();
        } catch (IOException ignored) {}
    }

    public void sensor(long tAbsMs, long sinceStart, String sensor, int accuracy, float[] values) {
        try {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < values.length; i++) {
                if (i > 0) sb.append('|');
                sb.append(String.format(Locale.US, "%.5f", values[i]));
            }
            sensorWriter.write(String.format(Locale.US, "%d,%d,%s,%d,%s\n", tAbsMs, sinceStart, sensor, accuracy, sb));
            sensorRows++;
            if (sensorRows % 256 == 0) sensorWriter.flush();
        } catch (IOException ignored) {}
    }

    public void close() {
        for (BufferedWriter w : new BufferedWriter[]{gameWriter, sensorWriter, metaWriter}) {
            try { if (w != null) { w.flush(); w.close(); } } catch (IOException ignored) {}
        }
    }

    public long getGameRows() { return gameRows; }
    public long getSensorRows() { return sensorRows; }

    public String wallClock() { return fmt.format(new Date()); }
}
