import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public class App extends Application {

    private Canvas canvas;
    private GraphicsContext gc;
    private GameState gs;

    @Override
    public void start(Stage primaryStage) {

        this.canvas = new Canvas(800, 600);
        this.gc = canvas.getGraphicsContext2D();

        StackPane root = new StackPane(canvas);
        Scene scene = new Scene(root, 800, 600, Color.BLACK);
        
        this.gs = new GameState(primaryStage, this.gc, this.canvas, scene);

        primaryStage.setTitle("GOL");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}