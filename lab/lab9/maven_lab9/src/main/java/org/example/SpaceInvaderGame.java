package org.example;
// mvn javafx:run
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SpaceInvaderGame extends Application {

    public static final int WIN_W = 600;
    public static final int WIN_H = 600;

    public static final int PLAYER_X = 350;
    public static final int PLAYER_Y = 550;
    public static final int PLAYER_W = 40;
    public static final int PLAYER_H = 40;

    public static final int ENEMY_X_START = 90;
    public static final int ENEMY_Y = 100;
    public static final int ENEMY_W = 30;
    public static final int ENEMY_H = 30;
    public static final int ENEMY_INTERVAL = 100;

    private final Pane root = new Pane();

    private final List<Line> EnemyBullet = new ArrayList<>();
    private final List<Line> PlayerBullet = new ArrayList<>();
    private final List<Sprite> EnemyList = new ArrayList<>();

    private final Sprite player = new Sprite(PLAYER_X, PLAYER_Y, PLAYER_W, PLAYER_H, "player", Color.BLUE);

    private AnimationTimer timer;

    private Parent createContent() {
        root.setPrefSize(WIN_W, WIN_H);
        root.getChildren().add(player);

        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                update();
            }
        };
        timer.start();

        createEnemies();
        return root;
    }

    private void createEnemies() {
        for (int i = 0; i < 5; i++) {
            Sprite s = new Sprite(ENEMY_X_START + i * ENEMY_INTERVAL, ENEMY_Y, ENEMY_W, ENEMY_H, "enemy", Color.RED);
            EnemyList.add(s);
            root.getChildren().add(s);
        }
    }

    private void update() {
        for (Sprite enemy : EnemyList) {
            if (enemy.dead) continue;
            if (Math.random() < 0.01) {
                Line temp = shoot(enemy);
                if (temp != null) EnemyBullet.add(temp);
            }
        }

        for (Iterator<Line> it = EnemyBullet.iterator(); it.hasNext(); ) {
            Line b = it.next();
            b.setStartY(b.getStartY() + 5);
            b.setEndY(b.getEndY() + 5);

            boolean remove = false;

            if (!player.dead && b.getBoundsInParent().intersects(player.getBoundsInParent())) {
                player.dead = true;
                remove = true;
            }

            if (isOutOfBounds(b)) remove = true;

            if (remove) {
                root.getChildren().remove(b);
                it.remove();
            }
        }

        for (Iterator<Line> it = PlayerBullet.iterator(); it.hasNext(); ) {
            Line b = it.next();
            b.setStartY(b.getStartY() - 8);
            b.setEndY(b.getEndY() - 8);

            boolean remove = false;

            for (Sprite enemy : EnemyList) {
                if (enemy.dead) continue;
                if (b.getBoundsInParent().intersects(enemy.getBoundsInParent())) {
                    enemy.dead = true;
                    remove = true;
                    break;
                }
            }

            if (isOutOfBounds(b)) remove = true;

            if (remove) {
                root.getChildren().remove(b);
                it.remove();
            }
        }

        root.getChildren().removeIf(n -> n instanceof Sprite s && s.dead);
    }

    private boolean isOutOfBounds(Line l) {
        double minY = Math.min(l.getStartY(), l.getEndY());
        double maxY = Math.max(l.getStartY(), l.getEndY());
        return maxY < 0 || minY > WIN_H;
    }

    private Line shoot(Sprite who) {
        if (who.dead) return null;

        Line line = new Line(
                who.getTranslateX() + 15,
                who.getTranslateY() + (who.type.equals("player") ? 0 : 30),
                who.getTranslateX() + 15,
                who.getTranslateY() + (who.type.equals("player") ? 5 : 35)
        );
        line.setStroke(Color.BLACK);
        line.setStrokeWidth(5);
        root.getChildren().add(line);
        return line;
    }

    @Override
    public void start(Stage stage) {
        Scene scene = new Scene(createContent());

        scene.setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case LEFT -> player.moveLeft();
                case RIGHT -> player.moveRight();
                case UP -> player.moveUp();
                case DOWN -> player.moveDown();
                case SPACE -> {
                    Line temp = shoot(player);
                    if (temp != null) PlayerBullet.add(temp);
                }
            }
        });

        stage.setScene(scene);
        stage.setTitle("Space Invader Game");
        stage.show();
    }

    private static class Sprite extends Rectangle {
        boolean dead = false;
        final String type;

        Sprite(int x, int y, int w, int h, String type, Color color) {
            super(w, h, color);
            this.type = type;
            setTranslateX(x);
            setTranslateY(y);
        }

        void moveLeft() {
            setTranslateX(Math.max(0, getTranslateX() - 5));
        }

        void moveRight() {
            setTranslateX(Math.min(WIN_W - getWidth(), getTranslateX() + 5));
        }

        void moveUp() {
            setTranslateY(Math.max(0, getTranslateY() - 5));
        }

        void moveDown() {
            setTranslateY(Math.min(WIN_H - getHeight(), getTranslateY() + 5));
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}