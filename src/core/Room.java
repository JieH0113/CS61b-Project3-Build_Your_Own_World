package core;

public class Room {
    public int x;
    public int y;
    public int width;
    public int height;
    public Point door;

    public Room(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.door = door;
    }

    //Avoiding overlap
    public boolean Overlap(Room anotherRoom) {
        return (this.x < anotherRoom.x + anotherRoom.width + 3 &&
                this.x + this.width + 3 > anotherRoom.x &&
                this.y < anotherRoom.y + anotherRoom.height &&
                this.y + this.height + 3 > anotherRoom.y);
    }
    public core.Point getCenter() {
        return new core.Point(x + width / 2, y + height / 2);
    }
}
