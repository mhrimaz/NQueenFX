/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nqueenfx;

import java.net.URL;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Group;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import org.jenetics.EnumGene;
import org.jenetics.LinearRankSelector;
import org.jenetics.Optimize;
import org.jenetics.PartiallyMatchedCrossover;
import org.jenetics.Phenotype;
import org.jenetics.SwapMutator;
import org.jenetics.TournamentSelector;
import org.jenetics.engine.Engine;
import static org.jenetics.engine.EvolutionResult.toBestPhenotype;
import org.jenetics.engine.EvolutionStatistics;
import org.jenetics.engine.codecs;
import org.jenetics.engine.limit;
import static org.jenetics.engine.limit.byFitnessThreshold;

/**
 *
 * @author mhrimaz
 */
public class FXMLDocumentController implements Initializable {

    @FXML
    private Button button;

    @FXML
    private StackPane stackPane;

    @FXML
    private TextField sizeField;

    @FXML
    private Label timeLabel;

    /**
     * with the power of the Java Parallel Stream this method will count the
     * number of queen that threat each other corisponding to the given queens
     * configuration
     *
     * @param config configuration of the queens in the chess board
     * @return total number of threat
     */
    private static long countDiagonal(final int[] config) {
        long sum = IntStream.range(0, config.length)
                .parallel()
                .mapToLong((i) -> {
                    return IntStream.range(0, config.length)
                            .parallel()
                            .filter((j) -> {
                                if (i != j) {
                                    int deltaRow = Math.abs(i - j);
                                    int deltaCol = Math.abs(config[i] - config[j]);
                                    return deltaRow == deltaCol;
                                } else {
                                    return false;
                                }
                            }).count();
                }).sum();
        return sum;
    }

    /**
     * this method will draw chess board with given queens configuration
     *
     * @param screenSize size of area for drawing chess board
     * @param config configuration of queens in the chess board
     * @return group which holds the chess elements
     */
    public static Group constructBoard(int screenSize, int config[]) {
        Group root = new Group();
        int n = config.length;
        boolean color = false;
        double size;
        size = (double) (screenSize / n);
        for (int j = 0, xPos = 0, yPos = 0; j < n; j++, xPos += size, yPos = 0) {
            for (int i = 0; i < n; i++, yPos += size, color = !color) {
                Rectangle piece = new Rectangle(xPos, yPos, size, size);
                if (color == false) {
                    piece.setFill(Color.BLACK);
                } else {
                    piece.setFill(Color.WHITE);
                }
                root.getChildren().add(piece);
                if (config[j] == i) {
                    Circle queen = new Circle(xPos + (double) size / 2,
                            yPos + (double) size / 2,
                            (double) size / 3);
                    queen.setFill(Color.RED);
                    root.getChildren().add(queen);
                }
            }
            if (n % 2 == 0) {
                color = !color;
            }
        }
        return root;
    }

    @FXML
    void executeOnAction(ActionEvent event) {
        if (!sizeField.getText().matches("\\d+") && Integer.parseInt(sizeField.getText()) > 3) {
            return;
        }
        Task<int[]> task = new Task<int[]>() {
            @Override
            protected void succeeded() {
                int[] resultConfig = this.getValue();
                stackPane.getChildren().clear();
                double size = Math.min(stackPane.getWidth(), stackPane.getHeight());
                stackPane.getChildren().add(constructBoard((int) size, resultConfig));
            }

            @Override
            protected void done() {
                button.setDisable(false);
            }

            @Override
            protected void running() {
                button.setDisable(true);
            }

            @Override
            protected int[] call() throws Exception {
                long start = System.currentTimeMillis();
                updateMessage("Executors Initialized");
                final int queensCount = Integer.parseInt(sizeField.getText());
                final ExecutorService executor = Executors.newFixedThreadPool(8);
                updateMessage("Engine Initialized");
                final Engine<EnumGene<Integer>, Long> engine = Engine
                        .builder(
                                FXMLDocumentController::countDiagonal,
                                codecs.ofPermutation(queensCount))
                        .optimize(Optimize.MINIMUM)
                        .survivorsSelector(new TournamentSelector<>(5))
                        .offspringSelector(new LinearRankSelector<>())
                        .populationSize(100)
                        .alterers(new SwapMutator<>(0.01),
                                new PartiallyMatchedCrossover<>(0.8))
                        .executor(executor)
                        .build();
                updateMessage("Engine Started, Please Wait");
                final Phenotype<EnumGene<Integer>, Long> best
                        = engine.stream()
                        .limit(byFitnessThreshold(1L))
                        .limit(limit.byExecutionTime(Duration.ofMinutes(30)))
                        .collect(toBestPhenotype());
                updateMessage("Executors Shutdown");
                executor.shutdown();
                long end = System.currentTimeMillis();
                updateMessage("Prepare Result");
                int[] resultConfig = best.getGenotype().getChromosome().stream().mapToInt((EnumGene gene) -> {
                    return (Integer) gene.getAllele();
                }).toArray();
                updateMessage("Total Time: " + (end - start) + " ms");
                return resultConfig;
            }
        };
        NQueenFX.mainExecutor.submit(task);
        timeLabel.textProperty().bind(task.messageProperty());
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // TODO
    }

}
