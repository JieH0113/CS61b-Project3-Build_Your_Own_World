package core;

import tileengine.TETile;
import tileengine.Tileset;
import utils.RandomUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;


public class World {
    public static class Room {
        public int x;
        public int y;
        public int width;
        public int height;

        public Room(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
//            this.door = door;
        }

        public boolean overlaps(Room other, int padding) {
            return (this.x - padding < other.x + other.width &&
                    this.x + this.width + padding > other.x &&
                    this.y - padding < other.y + other.height &&
                    this.y + this.height + padding > other.y);
        }

        public Point getCenter() {
            return new Point(x + width / 2, y + height / 2);
        }
    }


    private static final int WIDTH = Main.WORLD_WIDTH;
    private static final int HEIGHT = Main.WORLD_HEIGHT;
    private TETile[][] world;
    private Random rand;

    public World() {
        world = new TETile[WIDTH][HEIGHT];
        initializeWorld();
    }

    private void initializeWorld() {
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                world[x][y] = Tileset.NOTHING;
            }
        }
    }

    public TETile[][] getWorld() {
        return world;
    }

    public void generateWorld(TETile[][] world, long seed) {
        this.world = world;
        this.rand = new Random(seed);

        initializeWorld();

        List<Room> rooms = generateRooms(rand);



        for (Room room : rooms) {
            drawRoom(room);
        }
        connectRoomsWithMST(rooms);
        generateWalls();
        removeDeadEnds();
        cleanRedundantWalls();
        ensureBoundary();
    }

//    private List<Room> generateRooms(int roomNum, Random random) {
//        List<Room> rooms = new ArrayList<>();
//        int attempts = 0;
//        while (rooms.size() < roomNum && attempts < roomNum * 10) {
//            int X = RandomUtils.uniform(random, 5, WIDTH - 25);
//            int Y = RandomUtils.uniform(random, 5, HEIGHT - 25);
//            int width = RandomUtils.uniform(random, 5, 20);
//            int height = RandomUtils.uniform(random, 5, 20);
//
//            Room newRoom = new Room(X, Y, width, height);
//            boolean overlaps = false;
//
//            for (Room r : rooms) {
//                if (newRoom.overlaps(r, 3)) {
//                    overlaps = true;
//                    break;
//                }
//            }
//
//            if (!overlaps) {
//                rooms.add(newRoom);
//            }
//
//            attempts++;
//        }
//        return rooms;
//    }

    private static class Edge implements Comparable<Edge> {
        int u;
        int v;
        int weight;

        public Edge(int u, int v, int weight) {
            this.u = u;
            this.v = v;
            this.weight = weight;
        }

        @Override
        public int compareTo(Edge other) {
            return Integer.compare(this.weight, other.weight);
        }
    }

    private List<Room> generateRooms(Random rand) {
        List<Room> rooms = new ArrayList<>();
        int roomCount = RandomUtils.uniform(rand, 70, 90);
        int attempts = 0;

        while (rooms.size() < roomCount && attempts < 2000) {
            int width = RandomUtils.uniform(rand, 8, 18);
            int height = RandomUtils.uniform(rand, 8, 18);
            int x = RandomUtils.uniform(rand, 2, WIDTH - width - 2);
            int y = RandomUtils.uniform(rand, 2, HEIGHT - height - 2);

            Room newRoom = new Room(x, y, width, height);
            boolean valid = true;

            for (Room room : rooms) {
                if (newRoom.overlaps(room, 4)) { 
                    valid = false;
                    break;
                }
            }

            if (x + width >= WIDTH - 1 || y + height >= HEIGHT - 1) {
                valid = false;
            }

            if (valid) {
                rooms.add(newRoom);
            }

            attempts++;
        }
        return rooms;
    }

    private void drawRoom(Room room) {
        for (int x = room.x + 1; x < room.x + room.width - 1; x++) {
            for (int y = room.y + 1; y < room.y + room.height - 1; y++) {
                if (x < WIDTH && y < HEIGHT) {
                    world[x][y] = Tileset.FLOOR;
                }
            }
        }

        for (int x = room.x; x < room.x + room.width; x++) {
            if (x < WIDTH) {
                if (room.y < HEIGHT) world[x][room.y] = Tileset.WALL;
                if (room.y + room.height - 1 < HEIGHT)
                    world[x][room.y + room.height - 1] = Tileset.WALL;
            }
        }

        for (int y = room.y; y < room.y + room.height; y++) {
            if (y < HEIGHT) {
                if (room.x < WIDTH) world[room.x][y] = Tileset.WALL;
                if (room.x + room.width - 1 < WIDTH)
                    world[room.x + room.width - 1][y] = Tileset.WALL;
            }
        }
    }

    private void connectRoomsWithMST(List<Room> rooms) {
        if (rooms.size() < 2) return;

        List<Edge> edges = new ArrayList<>();
        for (int i = 0; i < rooms.size(); i++) {
            for (int j = i + 1; j < rooms.size(); j++) {
                Point p1 = rooms.get(i).getCenter();
                Point p2 = rooms.get(j).getCenter();
                int dist = p1.distanceSquared(p2);
                edges.add(new Edge(i, j, dist));
            }
        }

        Collections.sort(edges);

        int[] parent = new int[rooms.size()];
        for (int i = 0; i < parent.length; i++) {
            parent[i] = i;
        }

        for (Edge edge : edges) {
            int rootU = find(parent, edge.u);
            int rootV = find(parent, edge.v);

            if (rootU != rootV) {
                parent[rootU] = rootV;
                createCorridorBetween(
                        rooms.get(edge.u).getCenter(),
                        rooms.get(edge.v).getCenter()
                );
            }
        }
    }

    private int find(int[] parent, int x) {
        if (parent[x] != x) {
            parent[x] = find(parent, parent[x]);
        }
        return parent[x];
    }

    private void createCorridorBetween(Point start, Point end) {
        if (rand.nextBoolean()) {
            createHorizontalCorridor(start.x, end.x, start.y);
            createVerticalCorridor(start.y, end.y, end.x);
        } else {
            createVerticalCorridor(start.y, end.y, start.x);
            createHorizontalCorridor(start.x, end.x, end.y);
        }

        addDoorIfWall(start.x, start.y);
        addDoorIfWall(end.x, end.y);
    }

    private void addDoorIfWall(int x, int y) {
        if (x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT) {
            if (world[x][y] == Tileset.WALL) {
                world[x][y] = Tileset.UNLOCKED_DOOR;
            }
        }
    }

    private void createHorizontalCorridor(int x1, int x2, int y) {
        int start = Math.min(x1, x2);
        int end = Math.max(x1, x2);

        for (int x = start; x <= end; x++) {
            if (x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT) {
                if (world[x][y] == Tileset.NOTHING || world[x][y] == Tileset.WALL) {
                    world[x][y] = Tileset.FLOOR;
                }
            }
        }
    }

    private void createVerticalCorridor(int y1, int y2, int x) {
        int start = Math.min(y1, y2);
        int end = Math.max(y1, y2);


        for (int y = start; y <= end; y++) {
            if (x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT) {
                if (world[x][y] == Tileset.NOTHING || world[x][y] == Tileset.WALL) {
                    world[x][y] = Tileset.FLOOR;
                }
            }
        }
    }

    private void generateWalls() {
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                if (world[x][y] == Tileset.NOTHING) {
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            if (dx == 0 && dy == 0) continue;

                            int nx = x + dx;
                            int ny = y + dy;

                            if (nx >= 0 && nx < WIDTH && ny >= 0 && ny < HEIGHT) {
                                if (world[nx][ny] == Tileset.FLOOR ||
                                        world[nx][ny] == Tileset.UNLOCKED_DOOR) {
                                    world[x][y] = Tileset.WALL;
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
    }
private void removeDeadEnds() {
    boolean changed;
    do {
        changed = false;
        for (int x = 1; x < WIDTH - 1; x++) {
            for (int y = 1; y < HEIGHT - 1; y++) {
                if (world[x][y] == Tileset.FLOOR && isDeadEnd(x, y) && !isInRoom(x, y)) {
                    world[x][y] = Tileset.NOTHING;
                    changed = true;
                }
            }
        }
    } while (changed);
}

    private boolean isDeadEnd(int x, int y) {
        int floorCount = 0;
        if (world[x+1][y] == Tileset.FLOOR || world[x+1][y] == Tileset.UNLOCKED_DOOR) floorCount++;
        if (world[x-1][y] == Tileset.FLOOR || world[x-1][y] == Tileset.UNLOCKED_DOOR) floorCount++;
        if (world[x][y+1] == Tileset.FLOOR || world[x][y+1] == Tileset.UNLOCKED_DOOR) floorCount++;
        if (world[x][y-1] == Tileset.FLOOR || world[x][y-1] == Tileset.UNLOCKED_DOOR) floorCount++;

        return floorCount <= 1;
    }
    private boolean isInRoom(int x, int y) {
        int wallCount = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                int nx = x + dx;
                int ny = y + dy;
                if (nx >= 0 && nx < WIDTH && ny >= 0 && ny < HEIGHT) {
                    if (world[nx][ny] == Tileset.WALL) {
                        wallCount++;
                    }
                }
            }
        }
        return wallCount >= 4;
    }

    private void cleanRedundantWalls() {
        for (int x = 1; x < WIDTH - 1; x++) {
            for (int y = 1; y < HEIGHT - 1; y++) {
                if (world[x][y] == Tileset.WALL) {
                    boolean adjacentToFloor = false;
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            if (dx == 0 && dy == 0) continue;
                            int nx = x + dx;
                            int ny = y + dy;
                            if (nx >= 0 && nx < WIDTH && ny >= 0 && ny < HEIGHT) {
                                if (world[nx][ny] == Tileset.FLOOR ||
                                        world[nx][ny] == Tileset.UNLOCKED_DOOR) {
                                    adjacentToFloor = true;
                                    break;
                                }
                            }
                        }
                        if (adjacentToFloor) break;
                    }
                    if (!adjacentToFloor) {
                        world[x][y] = Tileset.NOTHING;
                    }
                }
            }
        }
    }

    private void ensureBoundary() {
        for (int x = 0; x < WIDTH; x++) {
            if (world[x][0] == Tileset.FLOOR) world[x][0] = Tileset.WALL;
            if (world[x][HEIGHT-1] == Tileset.FLOOR) world[x][HEIGHT-1] = Tileset.WALL;
        }
        for (int y = 0; y < HEIGHT; y++) {
            if (world[0][y] == Tileset.FLOOR) world[0][y] = Tileset.WALL;
            if (world[WIDTH-1][y] == Tileset.FLOOR) world[WIDTH-1][y] = Tileset.WALL;
        }
    }

    public Point findStartPosition() {
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                if (world[x][y] == Tileset.FLOOR) {
                    return new Point(x, y);
                }
            }
        }
        return new Point(1, 1);
    }

    public TETile[][] getWorldCopy() {
        TETile[][] copy = new TETile[WIDTH][HEIGHT];
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                copy[x][y] = world[x][y];
            }
        }
        return copy;
    }

}