import javafx.animation.AnimationTimer;

public class AnimationHandler {
    
    private static GameState gs;
    private AnimationTimer gameLoop;

    private long lastTime = 0;
    private final long interval = 500_000; // 5 ms in nanoseconds

    public AnimationHandler(GameState gameState) {
        AnimationHandler.gs = gameState;
        setupGameLoop();
    }

    private void setupGameLoop() {
        this.gameLoop = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (now - lastTime >= interval) {
                    lastTime = now;
                    AnimationHandler.gs.update(now);
                }
            }
        };
    }

    public void startGameLoop() {
        gameLoop.start();
    }
}
