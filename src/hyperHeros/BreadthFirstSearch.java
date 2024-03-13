package hyperHeros;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;

public class BreadthFirstSearch {
    private static final int GRID_WIDTH = 31;
    private static final int GRID_HEIGHT = 21;
    private static final int SCALING_FACTOR = 100;

    static class GridNode {
        GridNode parent;
        int x, y;

        GridNode(int x, int y, GridNode parent) {
            this.x = x;
            this.y = y;
            this.parent = parent;
        }
    }

    public static ArrayList<Coordonnate> findPath(Coordonnate start, Coordonnate end, ArrayList<Coordonnate> obstacles) {
        boolean[][] visited = new boolean[GRID_WIDTH][GRID_HEIGHT];
        GridNode[][] nodes = new GridNode[GRID_WIDTH][GRID_HEIGHT];

        // Initialize nodes and visited array
        initializeNodesAndVisited(nodes, visited, obstacles);

        Queue<GridNode> queue = new LinkedList<>();
        GridNode startNode = nodes[(int) (start.getX() / SCALING_FACTOR)][(int) (start.getY() / SCALING_FACTOR)];
        queue.add(startNode);
        visited[startNode.x][startNode.y] = true;

        while (!queue.isEmpty()) {
            GridNode current = queue.poll();
            if (isDestination(current, end)) {
                return reconstructPath(current);
            }

            ArrayList<GridNode> neighbors = getNeighbors(current, nodes, visited);
            for (GridNode neighbor : neighbors) {
                if (!visited[neighbor.x][neighbor.y]) {
                    visited[neighbor.x][neighbor.y] = true;
                    neighbor.parent = current;
                    queue.add(neighbor);
                }
            }
        }

        return new ArrayList<>(); // Return an empty path if no path is found.
    }

    private static void initializeNodesAndVisited(GridNode[][] nodes, boolean[][] visited, ArrayList<Coordonnate> obstacles) {
        for (int i = 0; i < GRID_WIDTH; i++) {
            for (int j = 0; j < GRID_HEIGHT; j++) {
                nodes[i][j] = new GridNode(i, j, null);
                visited[i][j] = isObstacle(i, j, obstacles);
            }
        }
    }

    private static boolean isObstacle(int x, int y, ArrayList<Coordonnate> obstacles) {
        for (Coordonnate obstacle : obstacles) {
            if (Math.abs(obstacle.getX() - x * SCALING_FACTOR) < SCALING_FACTOR && Math.abs(obstacle.getY() - y * SCALING_FACTOR) < SCALING_FACTOR) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDestination(GridNode node, Coordonnate end) {
        return node.x == end.getX() / SCALING_FACTOR && node.y == end.getY() / SCALING_FACTOR;
    }

    private static ArrayList<GridNode> getNeighbors(GridNode node, GridNode[][] nodes, boolean[][] visited) {
        ArrayList<GridNode> neighbors = new ArrayList<>();
        int[] dx = {0, 1, 0, -1};
        int[] dy = {1, 0, -1, 0}; // Directions: Right, Down, Left, Up

        for (int i = 0; i < dx.length; i++) {
            int nx = node.x + dx[i], ny = node.y + dy[i];
            if (nx >= 0 && nx < GRID_WIDTH && ny >= 0 && ny < GRID_HEIGHT && !visited[nx][ny]) {
                neighbors.add(nodes[nx][ny]);
            }
        }

        return neighbors;
    }

    private static ArrayList<Coordonnate> reconstructPath(GridNode current) {
        LinkedList<Coordonnate> path = new LinkedList<>();
        while (current != null) {
            path.addFirst(new Coordonnate(current.x * SCALING_FACTOR, current.y * SCALING_FACTOR));
            current = current.parent;
        }
        return new ArrayList<>(path);
    }

    public static void main(String[] args) {
        Coordonnate start = new Coordonnate(250, 850);
        Coordonnate end = new Coordonnate(350, 450);
        ArrayList<Coordonnate> obstacles = new ArrayList<>();
        // Add obstacles here
        ArrayList<Coordonnate> path = findPath(start, end, obstacles);
        System.out.println(path);
    }
}
