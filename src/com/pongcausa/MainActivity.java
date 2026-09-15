package com.pongcausa;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.*;
import android.text.InputType;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Replicação da monografia MAC0499 (Gasparino, 2021) com sensores de celular:
 * - Pong multiplayer (2 celulares via Wi-Fi) OU contra bot — jogador NÃO sabe qual (cego).
 * - Coleta de sensores (acelerômetro, giroscópio, etc.) análoga ao índice RR do Polar H7.
 * - Log de posições dos paddles/bola/pontuação a cada ~15ms.
 * - Séries derivadas (intervalos entre eventos do acelerômetro = análogo RR) para
 *   análise de causalidade de Granger vs posições no Pong.
 */
public class MainActivity extends Activity implements SensorEventListener, NetLink.Listener {

    // ---- jogo ----
    static final int W = 640, H = 400;
    static final int PADDLE_H = 80, PADDLE_W = 12, BALL_R = 8;
    static final float PADDLE_V = 260f; // px/s (mesma velocidade p/ jogador e bot)
    Ball ball;
    float p1y = H/2f, p2y = H/2f;
    int score1 = 0, score2 = 0;
    volatile float remoteTarget = H/2f, localTarget = H/2f;
    boolean isHost;
    boolean playing = false;
    long gameStartMs = 0, lastFrameMs = 0;
    long durationMs = 10 * 60 * 1000; // 10 minutos como na monografia

    // modo cego: sorteado/definido pelo fluxo
    boolean vsBot = false;         // host decide; cliente obedece; ambos não sabem
    boolean modeAssigned = false;

    // ---- sensores ----
    SensorManager sm;
    Sensor accS, gyrS, magS, hrS;
    SensorCatalog catalog;
    int accRate = 0;
    long lastAccTs = 0;
    double lastAccMag = 0;
    List<Long> beatTs = new ArrayList<>();    // "batidas" = picos no acelerômetro (análogo onda R)
    List<Double> rrLike = new ArrayList<>();  // intervalos entre picos (análogo RR)

    // ---- rede ----
    NetLink net;

    // ---- log ----
    GameLog log;
    String sessionId;
    File baseDir;

    // ---- ui ----
    PongView view;
    TextView statusText;
    LinearLayout setupPanel;
    EditText ipEdit;
    boolean sensorsOn = false;

    Handler ui = new Handler();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        catalog = new SensorCatalog(sm);
        baseDir = new File(getExternalFilesDir(null), "sessions");
        baseDir.mkdirs();
        setupUi();
    }

    void setupUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        setBackgroundColorDark(root);

        statusText = new TextView(this);
        statusText.setPadding(20, 12, 20, 12);
        statusText.setTextColor(0xFFFFFFFF);

        view = new PongView(this);
        view.setBackgroundColor(0xFF000000);

        setupPanel = new LinearLayout(this);
        setupPanel.setOrientation(LinearLayout.VERTICAL);
        setupPanel.setPadding(30, 20, 30, 20);

        TextView title = new TextView(this);
        title.setText("PONG — Experimento de Causalidade");
        title.setTextColor(0xFFE0E0E0);
        title.setTextSize(20);
        setupPanel.addView(title);

        TextView sens = new TextView(this);
        StringBuilder sb = new StringBuilder();
        sb.append("Sensores disponíveis (").append(catalog.count).append("):\n");
        sb.append("  acelerômetro: ").append(catalog.accelerometer ? "SIM" : "não").append('\n');
        sb.append("  giroscópio: ").append(catalog.gyroscope ? "SIM" : "não").append('\n');
        sb.append("  magnetômetro: ").append(catalog.magnetometer ? "SIM" : "não").append('\n');
        sb.append("  frequência cardíaca: ").append(catalog.heartRate ? "SIM" : "não").append('\n');
        sb.append("  contador de passos: ").append(catalog.stepCounter ? "SIM" : "não").append('\n');
        sb.append("  proximidade: ").append(catalog.proximity ? "SIM" : "não").append('\n');
        sb.append("  luz: ").append(catalog.light ? "SIM" : "não").append('\n');
        sens.setText(sb.toString());
        sens.setTextColor(0xFFB0B0B0);
        sens.setTextSize(12);
        setupPanel.addView(sens);

        Button hostBtn = mkButton("CRIAR PARTIDA (celular 1)", v -> startAsHost());
        Button joinBtn = mkButton("ENTRAR NA PARTIDA (celular 2)", v -> startAsClient());
        setupPanel.addView(hostBtn);
        setupPanel.addView(joinBtn);

        ipEdit = new EditText(this);
        ipEdit.setHint("IP do celular 1: " + NetLink.localIp());
        ipEdit.setInputType(InputType.TYPE_CLASS_PHONE);
        setupPanel.addView(ipEdit);

        Button soloBtn = mkButton("JOGAR AGORA (1 celular)", v -> {
            // modo solitário cego: pode ser contra bot ou contra "espelho humano" (jogue e descubra depois)
            vsBot = Math.random() < 0.5;
            soloTwoPhone = false;
            beginPlay();
        });
        TextView note = new TextView(this);
        note.setText("Você não saberá se o oponente é um bot ou uma pessoa. Os dados de sensores e do jogo serão salvos para análise de causalidade.");
        note.setTextColor(0xFF909090);
        note.setTextSize(11);
        setupPanel.addView(soloBtn);
        setupPanel.addView(note);

        root.addView(statusText);
        FrameLayout gameFrame = new FrameLayout(this);
        gameFrame.addView(view);
        root.addView(gameFrame, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(setupPanel, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
        showSetup(true);
    }

    boolean soloTwoPhone = false;

    Button mkButton(String label, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setOnClickListener(l);
        return b;
    }

    void setBackgroundColorDark(View v) { v.setBackgroundColor(0xFF101010); }

    void showSetup(boolean show) {
        setupPanel.setVisibility(show ? View.VISIBLE : View.GONE);
        view.setActive(!show);
        statusText.setText(show ? "IP local: " + NetLink.localIp() + "  |  aguardando configuração" : "");
    }

    void startAsHost() {
        sessionId = "exp_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        isHost = true;
        try {
            net = new NetLink(this);
            net.startHost();
            statusText.setText("Aguardando o outro celular entrar com o IP: " + NetLink.localIp());
            showSetup(false);
        } catch (Exception e) {
            Toast.makeText(this, "Erro: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    void startAsClient() {
        String ip = ipEdit.getText().toString().trim();
        if (ip.isEmpty()) { Toast.makeText(this, "Digite o IP do celular 1", Toast.LENGTH_SHORT).show(); return; }
        sessionId = "exp_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        isHost = false;
        try {
            net = new NetLink(this);
            net.startClient(ip);
            statusText.setText("Conectando a " + ip + " ...");
            showSetup(false);
        } catch (Exception e) {
            Toast.makeText(this, "Erro: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ---------- NetLink.Listener ----------
    @Override
    public void onPeerConnected(String who) {
        ui.post(() -> {
            // Host sorteia o modo (bot ou humano) e comunica; NINGUÉM informa o jogador.
            if (isHost) {
                vsBot = Math.random() < 0.5;
                net.send("mode|" + (vsBot ? "bot" : "human"));
                soloTwoPhone = true;
                beginPlay();
            } else {
                statusText.setText("Conectado! Iniciando...");
            }
        });
    }

    @Override
    public void onMessage(String msg) {
        // cliente recebe modo definido pelo host
        if (msg.startsWith("mode|")) {
            vsBot = "bot".equals(msg.substring(5));
            soloTwoPhone = true;
            ui.post(this::beginPlay);
        } else if (msg.startsWith("paddle|")) {
            try { remoteTarget = Float.parseFloat(msg.split("\\|")[1]); } catch (Exception ignored) {}
        } else if (msg.startsWith("score|")) {
            String[] p = msg.split("\\|");
            try { score1 = Integer.parseInt(p[1]); score2 = Integer.parseInt(p[2]); } catch (Exception ignored) {}
        } else if (msg.startsWith("event|")) {
            // eventos compartilhados: rebatida/ponto pelo par
        }
    }

    // ---------- ciclo de jogo ----------
    void beginPlay() {
        if (playing) return;
        playing = true;
        modeAssigned = true;
        gameStartMs = System.currentTimeMillis();
        lastFrameMs = gameStartMs;
        ball = new Ball();
        resetBall(true);
        startSensors();
        log = new GameLog(baseDir, sessionId);
        try { log.open(); } catch (Exception ignored) {}
        log.meta("started_at", log.wallClock());
        log.meta("role", isHost ? (soloTwoPhone ? "host/multiplayer" : "solo") : "client");
        log.meta("duration_ms", String.valueOf(durationMs));
        log.meta("screen", W + "x" + H);
        log.meta("paddle_speed_px_s", String.valueOf(PADDLE_V));
        log.meta("opponent", "hidden"); // cego
        log.meta("ip", NetLink.localIp());
        showSetup(false);
        ui.postDelayed(this::endPlay, durationMs);
        view.setActive(true);
    }

    void endPlay() {
        if (!playing) return;
        playing = false;
        view.setActive(false);
        stopSensors();
        log.meta("ended_at", log.wallClock());
        log.meta("final_score", score1 + "-" + score2);
        log.meta("game_rows", String.valueOf(log.getGameRows()));
        log.meta("sensor_rows", String.valueOf(log.getSensorRows()));
        log.meta("n_beats", String.valueOf(beatTs.size()));
        log.meta("n_rrlike", String.valueOf(rrLike.size()));
        log.meta("opponent_reveal", vsBot ? "bot" : (soloTwoPhone ? "human" : "solo-" + (vsBot ? "bot" : "mirror")));
        log.close();
        if (net != null) { net.send("event|end"); net.close(); }
        ui.post(() -> {
            AlertDialogBox();
        });
    }

    void AlertDialogBox() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        StringBuilder sb = new StringBuilder();
        sb.append("Partida encerrada.\n\nPlacar: ").append(score1).append(" x ").append(score2).append('\n');
        sb.append("Picos detectados (acelerômetro): ").append(beatTs.size()).append('\n');
        sb.append("Intervalos tipo-RR gerados: ").append(rrLike.size()).append('\n');
        sb.append("Dados salvos em:\n").append(new File(baseDir, sessionId).getAbsolutePath()).append('\n');
        sb.append("\nDeseja jogar outra partida?");
        b.setTitle("Fim do experimento")
         .setMessage(sb.toString())
         .setPositiveButton("Nova partida", (d, w) -> recreate())
         .setNegativeButton("Sair", (d, w) -> finish())
         .setCancelable(false)
         .show();
    }

    void resetBall(boolean random) {
        ball.x = W/2f; ball.y = H/2f;
        double ang = (Math.random() * 0.6 - 0.3);
        if (random && Math.random() < 0.5) ang = Math.PI - ang;
        ball.vx = (float)(Math.cos(ang) * 180);
        ball.vy = (float)(Math.sin(ang) * 180);
        if (Math.random() < 0.5) ball.vy = -ball.vy;
        ball.speedBoost = 1f;
    }

    /** chamado a cada frame pelo PongView */
    void tick(long nowMs, float dt) {
        if (!playing) return;

        // paddle local persegue localTarget (definido por toque/tela)
        p1y = approach(p1y, localTarget, PADDLE_V * dt);

        if (soloTwoPhone && !isHost) {
            // cliente: envia seu paddle como p2; host simula
            net.send("paddle|" + p1y);
            p2y = remoteTarget;
            // espelha bola do host (aproximação visual; a física oficial é do host)
        } else if (soloTwoPhone && isHost) {
            // host: p1 é local; p2 chega via rede (humano) OU é simulado (bot)
            if (vsBot) botMove(dt);
            p2y = approach(p2y, remoteTarget, PADDLE_V * dt);
        } else {
            // solo: oponente sempre controlado internamente (bot ou "humano-espelho")
            if (vsBot) botMove(dt);
            else mirrorMove(dt);
            p2y = approach(p2y, remoteTarget, PADDLE_V * dt);
        }

        if (soloTwoPhone && !isHost) {
            // cliente não simula física (evita divergência); apenas registra estado recebido
            // (posições do paddle remoto já aplicadas)
        } else {
            physics(dt);
        }

        logGame(nowMs);
    }

    void botMove(float dt) {
        // bot simples: sempre vai em direção à bola (como na monografia, mesma velocidade do jogador)
        float target = ball.y;
        remoteTarget = target;
    }

    void mirrorMove(float dt) {
        // "humano-espelho": persegue a bola com atraso de reação e ruído (imita humano)
        float noise = (float)(Math.sin(SystemClock.elapsedRealtime() * 0.013) * 30f
                + (Math.random() - 0.5) * 18f);
        float target = ball.y + noise;
        remoteTarget = target;
    }

    float approach(float from, float to, float maxStep) {
        float d = to - from;
        if (Math.abs(d) <= maxStep) return clamp(to, PADDLE_H/2f, H - PADDLE_H/2f);
        return clamp(from + Math.signum(d) * maxStep, PADDLE_H/2f, H - PADDLE_H/2f);
    }

    float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }

    void physics(float dt) {
        ball.x += ball.vx * ball.speedBoost * dt;
        ball.y += ball.vy * ball.speedBoost * dt;

        if (ball.y < BALL_R) { ball.y = BALL_R; ball.vy = -ball.vy; logGameEvent("wall_top"); }
        if (ball.y > H - BALL_R) { ball.y = H - BALL_R; ball.vy = -ball.vy; logGameEvent("wall_bottom"); }

        // paddle esquerdo (p1, jogador)
        if (ball.vx < 0 && ball.x - BALL_R <= PADDLE_W + 4 && ball.x - BALL_R > 0) {
            if (Math.abs(ball.y - p1y) <= PADDLE_H/2f + BALL_R) {
                bounce(p1y, 1); logGameEvent("hit_p1");
            }
        }
        // paddle direito (p2, oponente)
        if (ball.vx > 0 && ball.x + BALL_R >= W - PADDLE_W - 4 && ball.x + BALL_R < W) {
            if (Math.abs(ball.y - p2y) <= PADDLE_H/2f + BALL_R) {
                bounce(p2y, -1); logGameEvent("hit_p2");
            }
        }

        if (ball.x < -20) { score2++; logGameEvent("score_p2"); resetBall(true); }
        if (ball.x > W + 20) { score1++; logGameEvent("score_p1"); resetBall(true); }
    }

    void bounce(float paddleY, int dir) {
        float rel = clamp((ball.y - paddleY) / (PADDLE_H/2f), -1f, 1f);
        double ang = rel * (Math.PI / 4.0);
        float sp = (float)Math.hypot(ball.vx, ball.vy);
        sp = Math.min(sp * 1.05f, 520f); // ganha velocidade a cada rebatida
        ball.vx = (float)(Math.cos(ang) * sp * dir);
        ball.vy = (float)(Math.sin(ang) * sp);
    }

    String pendingEvent = "";

    void logGameEvent(String ev) { pendingEvent = ev; }

    void logGame(long nowMs) {
        long tAbs = System.currentTimeMillis();
        long since = tAbs - gameStartMs;
        float sp = (float) Math.hypot(ball.vx, ball.vy) * ball.speedBoost;
        log.game(tAbs, since, ball.x, ball.y, ball.vx * ball.speedBoost, ball.vy * ball.speedBoost, sp,
                p1y, p2y, score1, score2, pendingEvent.isEmpty() ? "-" : pendingEvent);
        pendingEvent = "";
    }

    // ---------- sensores ----------
    void startSensors() {
        if (sensorsOn) return;
        sensorsOn = true;
        accS = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyrS = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        magS = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        hrS = sm.getDefaultSensor(Sensor.TYPE_HEART_RATE);
        log.meta("sensor_accelerometer", accS != null ? accS.getName() : "none");
        log.meta("sensor_gyroscope", gyrS != null ? gyrS.getName() : "none");
        log.meta("sensor_magnetometer", magS != null ? magS.getName() : "none");
        log.meta("sensor_heartrate", hrS != null ? hrS.getName() : "none");
        int rateUs = 10000; // ~100Hz
        if (accS != null) sm.registerListener(this, accS, rateUs);
        if (gyrS != null) sm.registerListener(this, gyrS, rateUs);
        if (magS != null) sm.registerListener(this, magS, rateUs);
        if (hrS != null) sm.registerListener(this, hrS, SensorManager.SENSOR_DELAY_NORMAL);
    }

    void stopSensors() {
        if (!sensorsOn) return;
        sensorsOn = false;
        try { sm.unregisterListener(this); } catch (Exception ignored) {}
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        if (e == null) return;
        if (e.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            double mag = Math.sqrt(e.values[0]*e.values[0] + e.values[1]*e.values[1] + e.values[2]*e.values[2]);
            long ts = e.timestamp;
            if (lastAccTs != 0) {
                // detector de picos simples: cruzamento de derivada + limiar (análogo à detecção de onda R)
                if (lastAccMag < mag && accRate == 0 && Math.abs(mag - 9.81) < 4.0) accRate = 1;
            }
            lastAccTs = ts; lastAccMag = mag;
        }
        if (log != null && playing) {
            long tAbs = System.currentTimeMillis();
            long since = tAbs - gameStartMs;
            String name = SensorCatalog.typeName(e.sensor.getType());
            log.sensor(tAbs, since, name, e.accuracy, e.values);
            // deriva intervalos tipo-RR do acelerômetro (picos de magnitude)
            if (e.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                double mag = Math.sqrt(e.values[0]*e.values[0] + e.values[1]*e.values[1] + e.values[2]*e.values[2]);
                detectPeak(mag, System.currentTimeMillis());
            }
        }
    }

    // detector de picos do acelerômetro -> série de intervalos (análogo RR)
    static final double PK_LO = 10.6, PK_HI = 13.0;
    boolean pkAbove = false;
    long lastPeakMs = 0;

    void detectPeak(double mag, long tMs) {
        if (mag > PK_LO && !pkAbove) {
            pkAbove = true;
            if (lastPeakMs > 0 && tMs - lastPeakMs >= 250 && tMs - lastPeakMs <= 2000) {
                beatTs.add(tMs);
                rrLike.add((double)(tMs - lastPeakMs));
            }
            lastPeakMs = tMs;
        } else if (mag < PK_LO - 0.3) {
            pkAbove = false;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor s, int a) {}

    @Override
    protected void onPause() { super.onPause(); stopSensors(); }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopSensors();
        if (net != null) net.close();
        if (log != null) log.close();
    }

    // ---------- view do jogo ----------
    class PongView extends View {
        boolean active = false;
        Paint paint = new Paint();
        Paint bg = new Paint();
        Paint txt = new Paint();

        PongView(Context c) {
            super(c);
            bg.setColor(0xFF000000);
            paint.setColor(0xFFFFFFFF);
            paint.setAntiAlias(true);
            txt.setColor(0xFF888888);
            txt.setTextSize(28);
        }

        void setActive(boolean a) { active = a; }

        @Override
        protected void onDraw(android.graphics.Canvas cv) {
            cv.drawRect(0, 0, W, H, bg);
            if (playing) {
                cv.drawText(String.valueOf(score1), W/2 - 60, 40, txt);
                cv.drawText(String.valueOf(score2), W/2 + 40, 40, txt);
                cv.drawLine(W/2, 0, W/2, H, paint);
                cv.drawRect(0, p1y - PADDLE_H/2, PADDLE_W, p1y + PADDLE_H/2, paint);
                cv.drawRect(W - PADDLE_W, p2y - PADDLE_H/2, W, p2y + PADDLE_H/2, paint);
                cv.drawCircle(ball.x, ball.y, BALL_R, paint);
            } else {
                txt.setTextAlign(android.graphics.Paint.Align.CENTER);
                cv.drawText("preparar...", W/2, H/2, txt);
                txt.setTextAlign(android.graphics.Paint.Align.LEFT);
            }
            if (active) postInvalidateDelayed(15); // ~15ms como na monografia
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            float y = ev.getY() * (H / (float) Math.max(1, getHeight()));
            float x = ev.getX() * (W / (float) Math.max(1, getWidth()));
            if (x < W/2f || !soloTwoPhone) localTarget = y; // metade esquerda controla
            return true;
        }
    }

    static class Ball {
        float x, y, vx, vy;
        float speedBoost = 1f;
    }

    static {
        // mantém classe utilitária carregada
    }
}
