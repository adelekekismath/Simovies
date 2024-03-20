package hyperHeros;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.Queue;

public class PathFinder {
    private static final int GRID_WIDTH = 31;
    private static final int GRID_HEIGHT = 21;
    private static final int SCALING_FACTOR = 100; // Grid scaling factor to match real-world coordinates.
    private static final int OBSTACLE_PENALTY = Integer.MAX_VALUE;

    /**
     * Represents a node in the grid with a reference to its parent node.
     */
    static class GridNode {
        GridNode parent;
        int x, y;

        GridNode(int x, int y, GridNode parent) {
            this.x = x;
            this.y = y;
            this.parent = parent;
        }

        @Override
        public String toString() {
            return String.format("GridNode{x=%d, y=%d}", x, y);
        }
    }

    /**
     * Calculates the shortest path from start to end coordinates avoiding
     * obstacles.
     *
     * @param start     The start coordinate.
     * @param end       The end coordinate.
     * @param obstacles A list of obstacles in the grid.
     * @return A list of Coordonnate representing the path from start to end,
     *         excluding obstacles.
     */
    public static ArrayList<Coordonnate> findPath(Coordonnate start, Coordonnate end,
            ArrayList<Coordonnate> obstacles) {
        int[][] grid = new int[GRID_WIDTH][GRID_HEIGHT];
        initializeGrid(grid, end, obstacles);
        GridNode[][] nodes = new GridNode[GRID_WIDTH][GRID_HEIGHT];
        initializeNodes(nodes);
        PriorityQueue<GridNode> openSet = new PriorityQueue<>(Comparator.comparingInt(node -> grid[node.x][node.y]));
        ArrayList<GridNode> closedSet = new ArrayList<>();
        GridNode startNode = nodes[(int) (start.getX() / SCALING_FACTOR)][(int) (start.getY() / SCALING_FACTOR)];
        openSet.add(startNode);
        while (!openSet.isEmpty()) {
            GridNode current = openSet.poll();
            closedSet.add(current);
            if (isDestination(current, end)) {
                return reconstructPath(current);
            }
            ArrayList<GridNode> neighbors = getNeighbors(current, nodes);
            for (GridNode neighbor : neighbors) {
                if (closedSet.contains(neighbor) || grid[neighbor.x][neighbor.y] == OBSTACLE_PENALTY)
                    continue;
                neighbor.parent = current;
                if (!openSet.contains(neighbor)) {
                    openSet.add(neighbor);
                }
            }
        }
        return new ArrayList<>(); // Return an empty path if no path is found.
    }

    private static void initializeGrid(int[][] grid, Coordonnate end, ArrayList<Coordonnate> obstacles) {
        for (int i = 0; i < GRID_WIDTH; i++) {
            for (int j = 0; j < GRID_HEIGHT; j++) {
                Coordonnate coord = new Coordonnate(i * SCALING_FACTOR, j * SCALING_FACTOR);
                boolean isObstacle = obstacles.stream().anyMatch(obstacle -> obstacle.distance(coord) < SCALING_FACTOR);
                grid[i][j] = isObstacle ? OBSTACLE_PENALTY : (int) Math.round(end.distance(coord));
            }
        }
    }

    private static void initializeNodes(GridNode[][] nodes) {
        for (int i = 0; i < GRID_WIDTH; i++) {
            for (int j = 0; j < GRID_HEIGHT; j++) {
                nodes[i][j] = new GridNode(i, j, null);
            }
        }
    }

    private static boolean isDestination(GridNode node, Coordonnate end) {
        return node.x == end.getX() / SCALING_FACTOR && node.y == end.getY() / SCALING_FACTOR;
    }

    private static ArrayList<Coordonnate> reconstructPath(GridNode current) {
        LinkedList<Coordonnate> path = new LinkedList<>();
        while (current != null) {
            path.addFirst(new Coordonnate(current.x * SCALING_FACTOR, current.y * SCALING_FACTOR));
            current = current.parent;
        }
        return new ArrayList<>(path);
    }

    private static ArrayList<GridNode> getNeighbors(GridNode node, GridNode[][] nodes) {
        ArrayList<GridNode> neighbors = new ArrayList<>();
        // Add neighbors with valid grid positions.
        if (node.x > 0)
            neighbors.add(nodes[node.x - 1][node.y]);
        if (node.x < GRID_WIDTH - 1)
            neighbors.add(nodes[node.x + 1][node.y]);
        if (node.y > 0)
            neighbors.add(nodes[node.x][node.y - 1]);
        if (node.y < GRID_HEIGHT - 1)
            neighbors.add(nodes[node.x][node.y + 1]);
        return neighbors;
    }

    public static void main(String[] args) {
        Coordonnate start = new Coordonnate(200, 200);
        Coordonnate end = new Coordonnate(200, 1000);
        ArrayList<Coordonnate> obstacles = new ArrayList<>();
        obstacles.add(new Coordonnate(200, 600));
        ArrayList<Coordonnate> path = findPath(start, end, obstacles);
        System.out.println(path);

    }

}