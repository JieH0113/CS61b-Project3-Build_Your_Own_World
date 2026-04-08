package core;

import edu.princeton.cs.algs4.StdDraw;
import tileengine.TERenderer;
import tileengine.TETile;
import tileengine.Tileset;
import utils.RandomUtils;

import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class Main {
    public static final int WIDTH = 90;
    public static final int HEIGHT = 60;
    public static final int WORLD_WIDTH = 300;  
    public static final int WORLD_HEIGHT = 200; 
    public static final String SAVE_FILE = "save.txt";

    private enum Theme { DEFAULT, DESERT, FOREST, SNOW }
    private static Theme theme;
    private static Map<TETile,TETile> themeMap;
    private static boolean waitForThemeChange = false;
    private static TETile[][] originalWorld;
    private static Point avatarPos;
    private static Point viewportOffset; 
    private static TERenderer ter;
    private static long seed;
    private static StringBuilder inputSequence = new StringBuilder();
    private static boolean waitForQuit = false;
    private static boolean gameStarted = false;

    public static void main(String[] args) {
        StdDraw.setCanvasSize(WIDTH * 16, HEIGHT * 16);
        StdDraw.setXscale(0, WIDTH);
        StdDraw.setYscale(0, HEIGHT);
        StdDraw.clear(Color.BLACK);
        StdDraw.enableDoubleBuffering();

        char choice = drawStartScreenAndWait();

        Theme[] vals = Theme.values();
        theme = vals[RandomUtils.uniform(new Random(), vals.length)];
        buildThemeMap();

        seed = 0;
        if (choice == 'n') {
//            while (StdDraw.hasNextKeyTyped()) {
//                StdDraw.nextKeyTyped();
//            }
            String input = getSeedFromInput();
            seed = parseSeed(input);
            gameStarted = true;
        } else if (choice == 'l') {
            if (loadGame()) {
                gameStarted = true;
            } else {
                System.out.println("No saved game found. Starting new game.");
                seed = 123456789L;
                gameStarted = true;
            }
        } else if (choice == 'q') {
            System.exit(0);
        }
        if (gameStarted) {
            ter = new TERenderer();
            ter.initialize(WIDTH, HEIGHT);

            World worldOb = new World();
            originalWorld = worldOb.getWorld();
            worldOb.generateWorld(originalWorld, seed);

            if (choice == 'n') {
                avatarPos = worldOb.findStartPosition();
            }
            //if loaded, position of avatar is loaded
//            avatarPos = worldOb.findStartPosition();
            if (viewportOffset == null) {
                viewportOffset = new Point(0, 0);

            }

            updateViewport();
            gameLoop();
        }
    }

    private static void gameLoop() {
        boolean quit = false;

        while (!quit) {
            if (StdDraw.hasNextKeyTyped()) {
                char c = Character.toLowerCase(StdDraw.nextKeyTyped());

                if (handleQuitCommand(c)) {
                    saveGame();
                    System.exit(0);
                }
                if (handleThemeChange(c)) {
                    showThemeMenu();
                    buildThemeMap();
                    renderFrame();
                }

                if (handleMovement(c)) {
                    inputSequence.append(c);
                }
            }

            renderFrame();

            drawHUD();

            StdDraw.pause(20);
        }
    }

    private static boolean handleQuitCommand(char c) {
        if (c == ':') {
            waitForQuit = true;
            return false;
        } else if (waitForQuit && c == 'q') {
            return true;
        } else if (waitForQuit) {
            waitForQuit = false;
        }
        return false;
    }
    private static boolean handleThemeChange (char c) {
        if (c == ';') {
            waitForThemeChange = true;
            return false;
        } else if (waitForThemeChange && c == 't') {
            return true;
        } else if (waitForThemeChange) {
            return false;
        }
        return false;
    }
    private static boolean handleMovement(char c) {
        int newX = avatarPos.x;
        int newY = avatarPos.y;

        switch (c) {
            case 'w': newY += 1; break;
            case 's': newY -= 1; break;
            case 'a': newX -= 1; break;
            case 'd': newX += 1; break;
            default: return false;
        }

        if (isValidPosition(newX, newY)) {
            avatarPos.x = newX;
            avatarPos.y = newY;
            
            updateViewport();
            
            return true;
        }
        return false;
    }

    private static boolean isValidPosition(int x, int y) {
        if (x < 0 || x >= WORLD_WIDTH || y < 0 || y >= WORLD_HEIGHT) {
            return false;
        }

        TETile tile = originalWorld[x][y];
        return tile == Tileset.FLOOR || tile == Tileset.UNLOCKED_DOOR;
    }

    private static void renderFrame() {
        TETile[][] currentWorld = new TETile[WIDTH][HEIGHT];
        
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                currentWorld[x][y] = Tileset.NOTHING;
            }
        }
        
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                int worldX = viewportOffset.x + x;
                int worldY = viewportOffset.y + y;
                
                if (worldX >= 0 && worldX < WORLD_WIDTH && worldY >= 0 && worldY < WORLD_HEIGHT) {
                    TETile raw = originalWorld[worldX][worldY];
                    currentWorld[x][y] = themeMap.getOrDefault(raw, raw);
                }
            }
        }
        
        int screenX = avatarPos.x - viewportOffset.x;
        int screenY = avatarPos.y - viewportOffset.y;
        
        if (screenX >= 0 && screenX < WIDTH && screenY >= 0 && screenY < HEIGHT) {
            TETile themedAvatar = themeMap.getOrDefault(Tileset.AVATAR, Tileset.AVATAR);
            currentWorld[screenX][screenY] = themedAvatar;
        }

        ter.renderFrame(currentWorld);
    }

    private static void drawHUD() {
        double mouseX = StdDraw.mouseX();
        double mouseY = StdDraw.mouseY();

        int tileX = (int) Math.floor(mouseX);
        int tileY = (int) Math.floor(mouseY);

        String tileDescription = "Nothing";
        if (tileX >= 0 && tileX < WIDTH && tileY >= 0 && tileY < HEIGHT) {
            int worldX = viewportOffset.x + tileX;
            int worldY = viewportOffset.y + tileY;
            
            if (worldX >= 0 && worldX < WORLD_WIDTH && worldY >= 0 && worldY < WORLD_HEIGHT) {
                TETile originalTile = originalWorld[worldX][worldY];
                TETile themedTile = themeMap.getOrDefault(originalTile, originalTile);
                tileDescription = themedTile.description();
            }
        }

        StdDraw.setPenColor(new Color(0, 0, 0, 180)); // 半透明黑色背景
        StdDraw.filledRectangle(WIDTH / 2.0, HEIGHT - 1, WIDTH / 2.0, 1);

        StdDraw.setPenColor(Color.WHITE);
        StdDraw.setFont(new Font("Monaco", Font.PLAIN, 16));
        StdDraw.textLeft(1, HEIGHT - 1, "Tile: " + tileDescription);

        StdDraw.text(WIDTH / 3.0, HEIGHT - 1, "Position: " + avatarPos.x + "," + avatarPos.y);

        StdDraw.textRight(WIDTH * 2 / 3.0, HEIGHT - 1, ":q to save and quit");
        StdDraw.textRight(WIDTH - 1, HEIGHT - 1, ";t to choose theme");

        StdDraw.show();
    }

    public static String getSeedFromInput() {
        StringBuilder input = new StringBuilder();
        boolean done = false;
        while (!done) {
            drawSeedScreen(input.toString());
            if (StdDraw.hasNextKeyTyped()) {
                char key = StdDraw.nextKeyTyped();
                if (Character.isDigit(key)) {
                    input.append(key);
                } else if (key == 's' || key == 'S') {
                    if (input.length() > 0) {
                        done = true;
                    }
                } else if (key == 'q' || key == 'Q') {
                    System.exit(0);
                }
            }
        }
        return input.toString();
    }

    public static long parseSeed(String input) {
        try {
            return Long.parseLong(input);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static void drawSeedScreen(String currentInput) {
        StdDraw.clear(Color.BLACK);
        StdDraw.setPenColor(Color.WHITE);

        StdDraw.setFont(new Font("Monaco", Font.BOLD, 40));
        StdDraw.text(WIDTH / 2.0, HEIGHT - 10, "CS61B: BYOW");

        StdDraw.setFont(new Font("Monaco", Font.PLAIN, 30));
        StdDraw.text(WIDTH / 2.0, HEIGHT / 2.0 + 5, "Enter seed followed by S");

        StdDraw.setFont(new Font("Monaco", Font.BOLD, 35));
        StdDraw.text(WIDTH / 2.0, HEIGHT / 2.0 - 5, currentInput);

        StdDraw.setFont(new Font("Monaco", Font.PLAIN, 20));
        StdDraw.text(WIDTH / 2.0, 5, "Press Q to quit");

        StdDraw.show();
    }

    public static char drawStartScreenAndWait() {
        while (true) {
            drawStartScreen();
            if (StdDraw.hasNextKeyTyped()) {
                char c = Character.toLowerCase(StdDraw.nextKeyTyped());
                if (c == 'n' || c == 'l' || c == 't' || c == 'q') {
                    return c;
                }
            }
        }
    }

    public static void drawStartScreen() {
        StdDraw.clear(Color.BLACK);
        StdDraw.setPenColor(Color.WHITE);

        StdDraw.setFont(new Font("Monaco", Font.BOLD, 40));
        StdDraw.text(WIDTH / 2.0, HEIGHT - 10, "CS61B: BYOW");

        StdDraw.setFont(new Font("Monaco", Font.PLAIN, 30));
        StdDraw.text(WIDTH / 2.0, HEIGHT / 2.0 + 5, "(N) New Game");
        StdDraw.text(WIDTH / 2.0, HEIGHT / 2.0 - 5, "(L) Load Game");
//        StdDraw.text(WIDTH / 2.0, HEIGHT / 2.0 - 15, "(T) Choose Theme");
        StdDraw.text(WIDTH / 2.0, HEIGHT / 2.0 - 15, "(Q) Quit Game");



        File saveFile = new File(SAVE_FILE);
        if (saveFile.exists()) {
            StdDraw.setFont(new Font("Monaco", Font.PLAIN, 20));
            StdDraw.text(WIDTH / 2.0, 10, "Saved game available");
        }

        StdDraw.show();
    }

    private static void saveGame() {
        try (FileWriter writer = new FileWriter(SAVE_FILE)) {
            writer.write("seed:" + seed + "\n");

            writer.write("sequence:" + inputSequence.toString() + "\n");

            writer.write("position:" + avatarPos.x + "," + avatarPos.y + "\n");

            writer.write("viewport:" + viewportOffset.x + "," + viewportOffset.y + "\n");

            System.out.println("Game saved successfully.");
        } catch (IOException e) {
            System.err.println("Failed to save game: " + e.getMessage());
        }
    }

    private static boolean loadGame() {
        try {
            List<String> lines = Files.readAllLines(Paths.get(SAVE_FILE));
            for (String line : lines) {
                if (line.startsWith("seed:")) {
                    seed = Long.parseLong(line.substring(5));
                } else if (line.startsWith("sequence:")) {
                    inputSequence = new StringBuilder(line.substring(9));
                } else if (line.startsWith("position:")) {
                    String[] coords = line.substring(9).split(",");
                    avatarPos = new Point(Integer.parseInt(coords[0]), Integer.parseInt(coords[1]));
                } else if (line.startsWith("viewport:")) {
                    String[] coords = line.substring(9).split(",");
                    viewportOffset = new Point(Integer.parseInt(coords[0]), Integer.parseInt(coords[1]));
                }
            }
            return true;
        } catch (IOException e) {
            System.err.println("Failed to load game: " + e.getMessage());
            return false;
        } catch (Exception e) {
            System.err.println("Invalid save file format: " + e.getMessage());
            return false;
        }
    }

    private static void buildThemeMap() {
        themeMap = new HashMap<>();
        switch (theme) {
            case DEFAULT:
                // identity mapping
                themeMap.put(Tileset.FLOOR, Tileset.FLOOR);
                themeMap.put(Tileset.WALL,  Tileset.WALL);
                themeMap.put(Tileset.UNLOCKED_DOOR, Tileset.UNLOCKED_DOOR);
                themeMap.put(Tileset.AVATAR, Tileset.AVATAR);
                break;
            case DESERT:
                themeMap.put(Tileset.FLOOR, Tileset.SAND);
                themeMap.put(Tileset.WALL,  Tileset.CACTUS);
                themeMap.put(Tileset.UNLOCKED_DOOR, Tileset.LOCKED_DOOR);
                themeMap.put(Tileset.AVATAR, Tileset.PEACH);
                break;
            case FOREST:
                themeMap.put(Tileset.FLOOR, Tileset.GRASS);
                themeMap.put(Tileset.WALL,  Tileset.TREE);
                themeMap.put(Tileset.UNLOCKED_DOOR, Tileset.LOCKED_DOOR);
                themeMap.put(Tileset.AVATAR, Tileset.MOUNTAIN);
                break;
            case SNOW:
                themeMap.put(Tileset.FLOOR, Tileset.ICE);
                themeMap.put(Tileset.WALL,  Tileset.SNOW);
                themeMap.put(Tileset.UNLOCKED_DOOR, Tileset.LOCKED_DOOR);
                themeMap.put(Tileset.AVATAR, Tileset.FLOWER);
                break;
        }
    }

    private static void updateViewport() {
        int centeredViewportX = avatarPos.x - WIDTH / 2;
        int centeredViewportY = avatarPos.y - HEIGHT / 2;
        
        int newViewportX = Math.max(0, Math.min(centeredViewportX, WORLD_WIDTH - WIDTH));
        int newViewportY = Math.max(0, Math.min(centeredViewportY, WORLD_HEIGHT - HEIGHT));
        
        viewportOffset.x = newViewportX;
        viewportOffset.y = newViewportY;
    }

    private static void showThemeMenu() {
        StdDraw.clear(Color.BLACK);
        StdDraw.setFont(new Font("Monaco", Font.BOLD, 40));
        StdDraw.setPenColor(Color.WHITE);
        StdDraw.text(WIDTH / 2.0, HEIGHT / 2.0 + 22, "Choose a Theme");

        StdDraw.setPenColor(Color.WHITE);
        StdDraw.setFont(new Font("Monaco", Font.PLAIN, 30));
        StdDraw.text(WIDTH / 2.0, HEIGHT / 2.0 + 15, "1 - DEFAULT");
        StdDraw.text(WIDTH / 2.0, HEIGHT / 2.0 + 8, "2 - DESERT");
        StdDraw.text(WIDTH / 2.0, HEIGHT / 2.0 + 1, "3 - FOREST");
        StdDraw.text(WIDTH / 2.0, HEIGHT / 2.0 - 6, "4 - SNOW");
        StdDraw.text(WIDTH / 2.0, HEIGHT / 2.0 - 13, "R - Random Theme");
        StdDraw.text(WIDTH / 2.0, HEIGHT / 2.0 - 20, "B - Back to Game");
        StdDraw.show();

        while (true) {
            if (StdDraw.hasNextKeyTyped()) {
                char key = StdDraw.nextKeyTyped();
                switch (key) {
                    case '1':
                        theme = Theme.DEFAULT;
                        return;
                    case '2':
                        theme = Theme.DESERT;
                        return;
                    case '3':
                        theme = Theme.FOREST;
                        return;
                    case '4':
                        theme = Theme.SNOW;
                        return;
                    case 'r':
                        Theme[] vals = Theme.values();
                        theme = vals[RandomUtils.uniform(new Random(), vals.length)];
                        return;
                    case 'b':
                        return;
                }
            }
        }
    }
}
