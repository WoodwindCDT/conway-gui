import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public class GameState {

    private final double GRID_SPACING = 50; // Initial spacing between grid lines
    private double scale = 1.0;
    private  double panX = 0.0;
    private  double panY = 0.0;
    
    // Using a Set to store the coordinates of the filled grid cells
    private Set<GridCell> filledCells = new HashSet<>();
    // store ref to primary stage
    private Stage pm; // here in case of options screen
    private AnimationHandler ah;
    private GraphicsContext gc;
    private Canvas canvas;
    private Scene scene;
    private Random random = new Random();

    private double lastMouseX;
    private double lastMouseY;

    private double lastKnownMouseX = 0;
    private double lastKnownMouseY = 0;
    
    public GameState(Stage primaryStage, GraphicsContext graphicsContext, Canvas canvas, Scene mainScence) {
        this.pm = primaryStage;
        this.ah = new AnimationHandler(this);
        this.gc = graphicsContext;
        this.canvas = canvas;
        this.scene = mainScence;

        // Allow canvas to resize with the scene
        this.canvas.widthProperty().bind(scene.widthProperty());
        this.canvas.heightProperty().bind(scene.heightProperty());

        // Redraw grid when the canvas size changes or when the set of filled cells changes (implicitly handled by calling drawGrid)
        this.canvas.widthProperty().addListener(_ -> drawGrid());
        this.canvas.heightProperty().addListener(_ -> drawGrid());

        // Mouse event handlers for panning
        scene.setOnMousePressed(event -> {
            // Only pan with primary mouse button
            if (event.isMiddleButtonDown()) {
                lastMouseX = event.getX();
                lastMouseY = event.getY();
            }
        });

        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.BACK_SPACE) {
                clearCells();
            } else if (event.getCode() == KeyCode.SPACE) {
                // add randomized pattern!

                // Get the grid coordinates corresponding to the last known mouse position
                double worldX = screenToWorldX(lastKnownMouseX);
                double worldY = screenToWorldY(lastKnownMouseY);

                int patternCenterX = (int) Math.floor(worldX / GRID_SPACING);
                int patternCenterY = (int) Math.floor(worldY / GRID_SPACING);

                // Define the size of the random pattern area (e.g., a 10x10 square)
                int patternSize = 10; // Size of the square area (e.g., 10 means from -5 to +4 around center)
                double density = 0.3; // Probability a cell becomes alive (e.g., 0.3 = 30% chance)

                // Get the current set of alive cells to add to
                Set<GridCell> currentAliveCells = this.filledCells; // Assuming gs.getCells() is accessible and modifiable

                // Generate cells within the defined square area around the center point
                for (int xOffset = -patternSize / 2; xOffset < patternSize - patternSize / 2; xOffset++) {
                    for (int yOffset = -patternSize / 2; yOffset < patternSize - patternSize / 2; yOffset++) {
                        int gridX = patternCenterX + xOffset;
                        int gridY = patternCenterY + yOffset;

                        // Randomly decide if this cell should be alive
                        if (random.nextDouble() < density) {
                            currentAliveCells.add(new GridCell(gridX, gridY));
                        }
                    }
                }

                drawGrid(); // Redraw to show the new random pattern
            }
            
        });

        scene.setOnMouseMoved(event -> {
            lastKnownMouseX = event.getX();
            lastKnownMouseY = event.getY();
        });

        scene.setOnMouseDragged(event -> {
            // Only pan with primary mouse button
            if (event.isMiddleButtonDown()) {
                double deltaX = event.getX() - lastMouseX;
                double deltaY = event.getY() - lastMouseY;

                panX += deltaX;
                panY += deltaY;

                lastMouseX = event.getX();
                lastMouseY = event.getY();

                drawGrid();
            }
        });

        // Mouse event handler for zooming
        scene.setOnScroll(event -> {
            double zoomFactor = Math.pow(1.01, event.getDeltaY());

            double screenX = event.getX();
            double screenY = event.getY();

            // Convert screen coordinates to world coordinates before zooming
            double worldX = screenToWorldX(screenX);
            double worldY = screenToWorldY(screenY);

            // Apply the zoom
            scale *= zoomFactor;

            // Convert the world point back to new screen coordinates after zooming
            double newScreenX = worldToScreenX(worldX);
            double newScreenY = worldToScreenY(worldY);

            // Adjust pan to keep the world point under the mouse
            panX += (screenX - newScreenX);
            panY += (screenY - newScreenY);

            drawGrid();
        });

        // Mouse event handler for clicking to select and fill a cell
        scene.setOnMouseClicked(event -> {
             if (event.getButton() == MouseButton.PRIMARY) {
                // Convert screen coordinates of the click to world coordinates
                double worldX = screenToWorldX(event.getX());
                double worldY = screenToWorldY(event.getY());

                // Convert world coordinates to grid cell coordinates
                int clickedGridX = (int) Math.floor(worldX / GRID_SPACING);
                int clickedGridY = (int) Math.floor(worldY / GRID_SPACING);

                createCell(clickedGridX, clickedGridY);

                drawGrid();
            }
        });

        this.ah.startGameLoop();
    }

    public void update(long now) {
        // Get the current set of alive cells from the grid state object
        Set<GridCell> currentAliveCells = this.filledCells;

        // Map to store the count of live neighbors for cells that are either
        // currently alive or are neighbors of currently alive cells.
        // Key: GridCell, Value: Count of live neighbors in the current generation
        Map<GridCell, Integer> neighborCounts = new HashMap<>();

        // Set to store the cells that will be alive in the next generation
        Set<GridCell> nextGenerationCells = new HashSet<>();

        // Define the relative coordinates of the 8 neighbors
        int[] dx = {-1, -1, -1, 0, 0, 1, 1, 1};
        int[] dy = {-1, 0, 1, -1, 1, -1, 0, 1};

        // Iterate through all currently alive cells to calculate neighbor counts
        for (GridCell aliveCell : currentAliveCells) {
            // Ensure the alive cell itself is considered in the neighborCounts map
            // This is important for checking its own state later.
            neighborCounts.putIfAbsent(aliveCell, 0);

            // Iterate through the neighbors of the alive cell
            for (int i = 0; i < 8; i++) {
                int neighborX = aliveCell.x + dx[i];
                int neighborY = aliveCell.y + dy[i];
                GridCell neighborCell = new GridCell(neighborX, neighborY);

                // Increment the neighbor count for this neighbor cell
                neighborCounts.put(neighborCell, neighborCounts.getOrDefault(neighborCell, 0) + 1);
            }
        }

        // Now, apply the Game of Life rules based on the neighbor counts
        // We only need to consider cells that were in the neighborCounts map,
        // as these are the only cells whose state could possibly change.
        for (Map.Entry<GridCell, Integer> entry : neighborCounts.entrySet()) {
            GridCell cell = entry.getKey();
            int liveNeighbors = entry.getValue();
            boolean isCurrentlyAlive = currentAliveCells.contains(cell);

            // Apply the rules:
            if (isCurrentlyAlive) {
                // Rule 1 & 3: A live cell with < 2 or > 3 live neighbors dies.
                // Rule 2: A live cell with 2 or 3 live neighbors lives.
                if (liveNeighbors == 2 || liveNeighbors == 3) {
                    nextGenerationCells.add(cell);
                }
            } else {
                // Rule 4: A dead cell with exactly 3 live neighbors becomes alive.
                if (liveNeighbors == 3) {
                    nextGenerationCells.add(cell);
                }
            }
        }

        setCells(nextGenerationCells); // Or equivalent update
    }


    private void createCell(int clickedGridX, int clickedGridY) {
        addCell(new GridCell(clickedGridX - 1, clickedGridY));
        addCell(new GridCell(clickedGridX, clickedGridY));
        addCell(new GridCell(clickedGridX + 1, clickedGridY));
    }

    public void setCells(Set<GridCell> newCells) {
        this.filledCells = newCells;
        drawGrid();
    }

    private void addCell(GridCell cell) { 
        this.filledCells.add(cell);
    }

    private void removeCell(GridCell cell) { 
        this.filledCells.remove(cell); 
    }

    private void clearCells() { 
        this.filledCells.clear(); 
    }

    public Set<GridCell> getCells() {
        return this.filledCells;
    }

    private void drawGrid() {
        this.gc.clearRect(0, 0, this.canvas.getWidth(), this.canvas.getHeight());

        // --- Draw filled cells ---
        this.gc.setFill(Color.WHITE);
        double cellScreenSize = this.GRID_SPACING * this.scale; // Size of a cell on screen

        for (GridCell cell : this.filledCells) {
            double cellWorldX = cell.x * this.GRID_SPACING;
            double cellWorldY = cell.y * this.GRID_SPACING;

            double cellScreenX = worldToScreenX(cellWorldX);
            double cellScreenY = worldToScreenY(cellWorldY);

            // Only draw the filled cell if it's within the visible screen area for efficiency
            if (cellScreenX + cellScreenSize > 0 && cellScreenX < canvas.getWidth() &&
                cellScreenY + cellScreenSize > 0 && cellScreenY < canvas.getHeight()) {
                    this.gc.fillRect(cellScreenX, cellScreenY, cellScreenSize, cellScreenSize);
            }
        }
        if (scale > 0.35) {
            this.gc.setStroke(Color.LIGHTGRAY);
            this.gc.setLineWidth(0.5);
        

            // Determine the visible area in "world" coordinates
            double screenWidth = canvas.getWidth();
            double screenHeight = canvas.getHeight();

            double worldMinX = screenToWorldX(0);
            double worldMaxX = screenToWorldX(screenWidth);
            double worldMinY = screenToWorldY(0);
            double worldMaxY = screenToWorldY(screenHeight);

            // Calculate the start and end grid lines to draw
            // Use Math.floor and Math.ceil to ensure grid lines just outside the view are drawn
            int startGridX = (int) Math.floor(worldMinX / this.GRID_SPACING);
            int endGridX = (int) Math.ceil(worldMaxX / this.GRID_SPACING);
            int startGridY = (int) Math.floor(worldMinY / this.GRID_SPACING);
            int endGridY = (int) Math.ceil(worldMaxY / this.GRID_SPACING);

            // Draw vertical grid lines
            for (int i = startGridX; i <= endGridX; i++) {
                double x = worldToScreenX(i * this.GRID_SPACING);
                this.gc.strokeLine(x, 0, x, screenHeight);
            }

            // Draw horizontal grid lines
            for (int i = startGridY; i <= endGridY; i++) {
                double y = worldToScreenY(i * this.GRID_SPACING);
                this.gc.strokeLine(0, y, screenWidth, y);
            }
        }
    }

    // Converts screen X coordinate to world X coordinate
    private double screenToWorldX(double screenX) {
        return (screenX - this.panX) / this.scale;
    }

    // Converts screen Y coordinate to world Y coordinate
    private double screenToWorldY(double screenY) {
        return (screenY - this.panY) / this.scale;
    }

    // Converts world X coordinate to screen X coordinate
    private double worldToScreenX(double worldX) {
        return this.panX + worldX * this.scale;
    }

    // Converts world Y coordinate to screen Y coordinate
    private double worldToScreenY(double worldY) {
        return this.panY + worldY * this.scale;
    }
}