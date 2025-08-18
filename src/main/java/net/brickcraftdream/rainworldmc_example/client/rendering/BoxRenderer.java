package net.brickcraftdream.rainworldmc_example.client.rendering;

import com.mojang.blaze3d.systems.RenderSystem;

import net.brickcraftdream.rainworldmc_example.networking.NetworkManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import org.joml.Matrix4f;

import java.util.*;

import static net.brickcraftdream.rainworldmc_example.Rainworldmc_example.MOD_ID;


public class BoxRenderer {
    private static final float LINE_WIDTH = 1.0F;
    private static final float EPSILON = 0.0003F;
    private static final float LINE_WIDTH_FULL = 0.01F;

    private static final List<BoxData> connectedBoxes = new ArrayList<>();
    private static final Map<UUID, List<BoxData>> otherPeoplesConnectedBoxes = new HashMap<>();


    public static List<GlobalPos> locations = new ArrayList<>();
    public static List<GlobalPos> otherPeoplesLocations = new ArrayList<>();
    public static List<GlobalPos> firstAndSecondLocations = new ArrayList<>();

    /**
     * Class to store a box using two corners.
     * Also provides internal methods to check if two boxes are connected.
     */
    public static class BoxData {
        public final int minX, minY, minZ;
        public final int maxX, maxY, maxZ;

        public BoxData(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        public Box toBox() {
            return new Box(minX, minY, minZ, maxX, maxY, maxZ);
        }

        public boolean isConnected(BoxData other) {
            Box thisBox = this.toBox();
            Box otherBox = other.toBox();

            return thisBox.intersects(otherBox) ||
                    isAdjacent(this.minX, this.maxX, other.minX, other.maxX) &&
                            isOverlapping(this.minY, this.maxY, other.minY, other.maxY) &&
                            isOverlapping(this.minZ, this.maxZ, other.minZ, other.maxZ) ||
                    isAdjacent(this.minY, this.maxY, other.minY, other.maxY) &&
                            isOverlapping(this.minX, this.maxX, other.minX, other.maxX) &&
                            isOverlapping(this.minZ, this.maxZ, other.minZ, other.maxZ) ||
                    isAdjacent(this.minZ, this.maxZ, other.minZ, other.maxZ) &&
                            isOverlapping(this.minX, this.maxX, other.minX, other.maxX) &&
                            isOverlapping(this.minY, this.maxY, other.minY, other.maxY);
        }

        private boolean isAdjacent(int min1, int max1, int min2, int max2) {
            return max1 == min2 || max2 == min1;
        }

        private boolean isOverlapping(int min1, int max1, int min2, int max2) {
            return (min1 <= min2 && max1 >= min2) || (min2 <= min1 && max2 >= min1);
        }
    }

    /**
     * Class to represent a face of a box.
     */
    private static class QuadFace {
        public final float minX, minY, minZ;
        public final float maxX, maxY, maxZ;
        public final Direction direction;

        public QuadFace(float minX, float minY, float minZ, float maxX, float maxY, float maxZ, Direction direction) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
            this.direction = direction;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            QuadFace quadFace = (QuadFace) o;
            return Float.compare(quadFace.minX, minX) == 0 &&
                    Float.compare(quadFace.minY, minY) == 0 &&
                    Float.compare(quadFace.minZ, minZ) == 0 &&
                    Float.compare(quadFace.maxX, maxX) == 0 &&
                    Float.compare(quadFace.maxY, maxY) == 0 &&
                    Float.compare(quadFace.maxZ, maxZ) == 0 &&
                    direction == quadFace.direction;
        }

        @Override
        public int hashCode() {
            return Objects.hash(minX, minY, minZ, maxX, maxY, maxZ, direction);
        }
    }

    /**
     * Class to represent an edge of a box.
     */
    private static class Edge {
        public final float x1, y1, z1, x2, y2, z2;

        public Edge(float x1, float y1, float z1, float x2, float y2, float z2) {
            this.x1 = x1;
            this.y1 = y1;
            this.z1 = z1;
            this.x2 = x2;
            this.y2 = y2;
            this.z2 = z2;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Edge edge = (Edge) o;
            return (Float.compare(edge.x1, x1) == 0 &&
                    Float.compare(edge.y1, y1) == 0 &&
                    Float.compare(edge.z1, z1) == 0 &&
                    Float.compare(edge.x2, x2) == 0 &&
                    Float.compare(edge.y2, y2) == 0 &&
                    Float.compare(edge.z2, z2) == 0) ||
                    (Float.compare(edge.x1, x2) == 0 &&
                            Float.compare(edge.y1, y2) == 0 &&
                            Float.compare(edge.z1, z2) == 0 &&
                            Float.compare(edge.x2, x1) == 0 &&
                            Float.compare(edge.y2, y1) == 0 &&
                            Float.compare(edge.z2, z1) == 0);
        }

        @Override
        public int hashCode() {
            // Order-independent hash
            return Objects.hash(
                    Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                    Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2)
            );
        }
    }

    /**
     * Add a box to the list of boxes owned by the client. Also adds all positions within the box to the locations list and sends a packet to update other clients with the new placed box
     * @param firstCorner a {@link BlockPos} indicating the first corner of the box
     * @param secondCorner a {@link BlockPos} indicating the second corner of the box
     * @param world the {@link ClientWorld} in which the box is being placed
     * @param playerName the {@link UUID} of the player placing the box
     */
    public static void addBox(BlockPos firstCorner, BlockPos secondCorner, ClientWorld world, UUID playerName) {
        if (firstCorner == null || secondCorner == null) return;

        int minX = Math.min(firstCorner.getX(), secondCorner.getX());
        int minY = Math.min(firstCorner.getY(), secondCorner.getY());
        int minZ = Math.min(firstCorner.getZ(), secondCorner.getZ());
        int maxX = Math.max(firstCorner.getX(), secondCorner.getX()) + 1;
        int maxY = Math.max(firstCorner.getY(), secondCorner.getY()) + 1;
        int maxZ = Math.max(firstCorner.getZ(), secondCorner.getZ()) + 1;
        RegistryKey<World> dimension = world.getRegistryKey();

        for(int x = minX; x < maxX; x++) {
            for(int y = minY; y < maxY; y++) {
                for(int z = minZ; z < maxZ; z++) {
                    GlobalPos globalPos = GlobalPos.create(dimension, new BlockPos(x, y, z));
                    locations.add(globalPos);
                }
            }
        }

        GlobalPos firstGlobalPos = GlobalPos.create(dimension, firstCorner);
        GlobalPos secondGlobalPos = GlobalPos.create(dimension, secondCorner);
        firstAndSecondLocations.add(firstGlobalPos);
        firstAndSecondLocations.add(secondGlobalPos);

        ClientPlayNetworking.send(new NetworkManager.SelectedLocationPayload(firstGlobalPos, secondGlobalPos, playerName));

        BoxData newBox = new BoxData(minX, minY, minZ, maxX, maxY, maxZ);
        connectedBoxes.add(newBox);
    }

    /**
     * Called only from a packet receiver. Adds a box to {@link #otherPeoplesConnectedBoxes} with the key being the player specified by <code>playerName</code>.
     * Also adds all positions within the box to {@link #otherPeoplesLocations}.
     * @param firstCorner       a {@link BlockPos} indicating the first corner of the box
     * @param secondCorner      a {@link BlockPos} indicating the second corner of the box
     * @param world             the {@link ClientWorld} in which the box is being placed
     * @param playerName        the {@link UUID} of the player placing the box, which is also the key for the {@link #otherPeoplesConnectedBoxes} map
     */
    public static void addOtherPeoplesBox(BlockPos firstCorner, BlockPos secondCorner, ClientWorld world, UUID playerName) {
        if (firstCorner == null || secondCorner == null) return;

        int minX = Math.min(firstCorner.getX(), secondCorner.getX());
        int minY = Math.min(firstCorner.getY(), secondCorner.getY());
        int minZ = Math.min(firstCorner.getZ(), secondCorner.getZ());
        int maxX = Math.max(firstCorner.getX(), secondCorner.getX()) + 1;
        int maxY = Math.max(firstCorner.getY(), secondCorner.getY()) + 1;
        int maxZ = Math.max(firstCorner.getZ(), secondCorner.getZ()) + 1;
        RegistryKey<World> dimension = world.getRegistryKey();

        for(int x = minX; x < maxX; x++) {
            for(int y = minY; y < maxY; y++) {
                for(int z = minZ; z < maxZ; z++) {
                    GlobalPos globalPos = GlobalPos.create(dimension, new BlockPos(x, y, z));
                    otherPeoplesLocations.add(globalPos);

                }
            }
        }

        BoxData newBox = new BoxData(minX, minY, minZ, maxX, maxY, maxZ);
        List<BoxData> list = otherPeoplesConnectedBoxes.getOrDefault(playerName, new ArrayList<>());
        list.add(newBox);
        otherPeoplesConnectedBoxes.put(playerName, list);
    }

    /**
     * Clears all boxes and locations.
     * This is partially used to reset the state when the player either deselects or confirms a placement.
     */
    public static void clearBoxes() {
        connectedBoxes.clear();
        locations.clear();
        firstAndSecondLocations.clear();
    }

    /**
     * Removes all boxes owned by the player specified by <code>playerName</code>.
     * This is used to reset the state when that player either deselects or confirms a placement.
     * @param playerName the {@link UUID} of the player whose boxes are being removed
     */
    public static void removeOtherPeoplesBoxesByUUID(UUID playerName) {
        otherPeoplesConnectedBoxes.remove(playerName);
    }

    @Deprecated
    public static List<GlobalPos> getLocations() {
        return locations;
    }

    /**
     * Internal method to find connected groups of boxes owned by the client, used for merging multiple boxes into one before rendering.
     * This method uses a breadth-first search to find all boxes that are connected to each other.
     * @return a list of sets, where each set contains boxes that are connected to each other
     */
    private static List<Set<BoxData>> findConnectedGroups() {
        List<Set<BoxData>> groups = new ArrayList<>();
        Set<BoxData> unprocessed = new HashSet<>(connectedBoxes);

        while (!unprocessed.isEmpty()) {
            Set<BoxData> currentGroup = new HashSet<>();
            Queue<BoxData> queue = new LinkedList<>();

            BoxData start = unprocessed.iterator().next();
            queue.add(start);
            unprocessed.remove(start);
            currentGroup.add(start);

            while (!queue.isEmpty()) {
                BoxData current = queue.poll();

                Iterator<BoxData> iterator = unprocessed.iterator();
                while (iterator.hasNext()) {
                    BoxData next = iterator.next();
                    if (current.isConnected(next)) {
                        queue.add(next);
                        currentGroup.add(next);
                        iterator.remove();
                    }
                }
            }

            groups.add(currentGroup);
        }

        return groups;
    }

    /**
     * Internal method to find connected groups of boxes owned by other players, used for merging multiple boxes into one before rendering.
     * This method uses a breadth-first search to find all boxes that are connected to each other for a specific player.
     * @param playerName the UUID of the player whose boxes are being processed
     * @return a list of sets, where each set contains boxes that are connected to each other for the specified player
     */
    private static List<Set<BoxData>> findOtherPeoplesConnectedGroups(UUID playerName) {
        List<Set<BoxData>> groups = new ArrayList<>();
        Set<BoxData> unprocessed = new HashSet<>(otherPeoplesConnectedBoxes.get(playerName));

        while (!unprocessed.isEmpty()) {
            Set<BoxData> currentGroup = new HashSet<>();
            Queue<BoxData> queue = new LinkedList<>();

            BoxData start = unprocessed.iterator().next();
            queue.add(start);
            unprocessed.remove(start);
            currentGroup.add(start);

            while (!queue.isEmpty()) {
                BoxData current = queue.poll();

                Iterator<BoxData> iterator = unprocessed.iterator();
                while (iterator.hasNext()) {
                    BoxData next = iterator.next();
                    if (current.isConnected(next)) {
                        queue.add(next);
                        currentGroup.add(next);
                        iterator.remove();
                    }
                }
            }

            groups.add(currentGroup);
        }

        return groups;
    }


    /**
     * Calculates the exterior faces of a group of boxes (really janky).
     * @param boxGroup a set of {@link BoxData} representing the group of boxes
     * @return a set of {@link QuadFace} representing the exterior faces of the boxes in the group
     */
    private static Set<QuadFace> calculateExteriorFaces(Set<BoxData> boxGroup) {
        Set<QuadFace> faces = new HashSet<>();

        for (BoxData box : boxGroup) {
            // Generate all 6 faces of the box
            List<QuadFace> boxFaces = new ArrayList<>();
            boxFaces.add(new QuadFace(box.minX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, Direction.DOWN));
            boxFaces.add(new QuadFace(box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, Direction.UP));
            boxFaces.add(new QuadFace(box.minX, box.minY, box.minZ, box.minX, box.maxY, box.maxZ, Direction.WEST));
            boxFaces.add(new QuadFace(box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, Direction.EAST));
            boxFaces.add(new QuadFace(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, Direction.NORTH));
            boxFaces.add(new QuadFace(box.minX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, Direction.SOUTH));

            // Process each face by splitting against all other boxes
            for (QuadFace face : boxFaces) {
                List<QuadFace> splitFaces = new ArrayList<>();
                splitFaces.add(face);

                for (BoxData otherBox : boxGroup) {
                    if (otherBox == box) continue; // Skip self
                    List<QuadFace> newSplitFaces = new ArrayList<>();
                    for (QuadFace splitFace : splitFaces) {
                        newSplitFaces.addAll(splitFace(splitFace, otherBox));
                    }
                    splitFaces = newSplitFaces;
                }

                faces.addAll(splitFaces);
            }
        }

        // Filter out interior faces
        Set<QuadFace> exteriorFaces = new HashSet<>();
        for (QuadFace face : faces) {
            // Face center
            double centerX = (face.minX + face.maxX) / 2.0;
            double centerY = (face.minY + face.maxY) / 2.0;
            double centerZ = (face.minZ + face.maxZ) / 2.0;

            // Move slightly outward along the face normal
            Vec3i normal = face.direction.getVector(); // works in both Mojang & Yarn
            double checkX = centerX + normal.getX() * EPSILON;
            double checkY = centerY + normal.getY() * EPSILON;
            double checkZ = centerZ + normal.getZ() * EPSILON;

            boolean isExterior = true;
            for (BoxData box : boxGroup) {
                if (checkX >= box.minX && checkX <= box.maxX &&
                        checkY >= box.minY && checkY <= box.maxY &&
                        checkZ >= box.minZ && checkZ <= box.maxZ) {
                    isExterior = false;
                    break;
                }
            }

            if (isExterior) {
                exteriorFaces.add(face);
            }
        }

        return exteriorFaces;
    }

    /**
     * Splits a face based on intersection with another box.
     * This method returns a list of new faces created by splitting the original face.
     * If there is no intersection, the original face is returned as a single element list.
     * @param face the {@link QuadFace} to split
     * @param box the {@link BoxData} to check intersection with
     * @return a list of {@link QuadFace} representing the split faces
     */
    private static List<QuadFace> splitFace(QuadFace face, BoxData box) {
        List<QuadFace> result = new ArrayList<>();

        // Check if face and box intersect
        if (!doIntersect(face, box)) {
            result.add(face); // No intersection, keep the original face
            return result;
        }

        // Calculate intersection bounds
        float intersectMinX = Math.max(face.minX, box.minX);
        float intersectMaxX = Math.min(face.maxX, box.maxX);
        float intersectMinY = Math.max(face.minY, box.minY);
        float intersectMaxY = Math.min(face.maxY, box.maxY);
        float intersectMinZ = Math.max(face.minZ, box.minZ);
        float intersectMaxZ = Math.min(face.maxZ, box.maxZ);

        // Based on face direction, split accordingly
        switch (face.direction) {
            case UP:
            case DOWN:
                // Split along X and Z
                // Up to 4 rectangles can be created around the intersection

                // Left of intersection (minX to intersectMinX)
                if (face.minX < intersectMinX) {
                    result.add(new QuadFace(
                            face.minX, face.minY, face.minZ,
                            intersectMinX, face.maxY, face.maxZ,
                            face.direction
                    ));
                }

                // Right of intersection (intersectMaxX to maxX)
                if (intersectMaxX < face.maxX) {
                    result.add(new QuadFace(
                            intersectMaxX, face.minY, face.minZ,
                            face.maxX, face.maxY, face.maxZ,
                            face.direction
                    ));
                }

                // Front of intersection (minZ to intersectMinZ)
                if (face.minZ < intersectMinZ) {
                    result.add(new QuadFace(
                            Math.max(face.minX, intersectMinX), face.minY, face.minZ,
                            Math.min(face.maxX, intersectMaxX), face.maxY, intersectMinZ,
                            face.direction
                    ));
                }

                // Back of intersection (intersectMaxZ to maxZ)
                if (intersectMaxZ < face.maxZ) {
                    result.add(new QuadFace(
                            Math.max(face.minX, intersectMinX), face.minY, intersectMaxZ,
                            Math.min(face.maxX, intersectMaxX), face.maxY, face.maxZ,
                            face.direction
                    ));
                }
                break;

            case EAST:
            case WEST:
                // Split along Y and Z

                // Bottom of intersection (minY to intersectMinY)
                if (face.minY < intersectMinY) {
                    result.add(new QuadFace(
                            face.minX, face.minY, face.minZ,
                            face.maxX, intersectMinY, face.maxZ,
                            face.direction
                    ));
                }

                // Top of intersection (intersectMaxY to maxY)
                if (intersectMaxY < face.maxY) {
                    result.add(new QuadFace(
                            face.minX, intersectMaxY, face.minZ,
                            face.maxX, face.maxY, face.maxZ,
                            face.direction
                    ));
                }

                // Front of intersection (minZ to intersectMinZ)
                if (face.minZ < intersectMinZ) {
                    result.add(new QuadFace(
                            face.minX, Math.max(face.minY, intersectMinY), face.minZ,
                            face.maxX, Math.min(face.maxY, intersectMaxY), intersectMinZ,
                            face.direction
                    ));
                }

                // Back of intersection (intersectMaxZ to maxZ)
                if (intersectMaxZ < face.maxZ) {
                    result.add(new QuadFace(
                            face.minX, Math.max(face.minY, intersectMinY), intersectMaxZ,
                            face.maxX, Math.min(face.maxY, intersectMaxY), face.maxZ,
                            face.direction
                    ));
                }
                break;

            case NORTH:
            case SOUTH:
                // Split along X and Y

                // Left of intersection (minX to intersectMinX)
                if (face.minX < intersectMinX) {
                    result.add(new QuadFace(
                            face.minX, face.minY, face.minZ,
                            intersectMinX, face.maxY, face.maxZ,
                            face.direction
                    ));
                }

                // Right of intersection (intersectMaxX to maxX)
                if (intersectMaxX < face.maxX) {
                    result.add(new QuadFace(
                            intersectMaxX, face.minY, face.minZ,
                            face.maxX, face.maxY, face.maxZ,
                            face.direction
                    ));
                }

                // Bottom of intersection (minY to intersectMinY)
                if (face.minY < intersectMinY) {
                    result.add(new QuadFace(
                            Math.max(face.minX, intersectMinX), face.minY, face.minZ,
                            Math.min(face.maxX, intersectMaxX), intersectMinY, face.maxZ,
                            face.direction
                    ));
                }

                // Top of intersection (intersectMaxY to maxY)
                if (intersectMaxY < face.maxY) {
                    result.add(new QuadFace(
                            Math.max(face.minX, intersectMinX), intersectMaxY, face.minZ,
                            Math.min(face.maxX, intersectMaxX), face.maxY, face.maxZ,
                            face.direction
                    ));
                }
                break;
        }

        // If no sub-faces were created, it means the face is entirely covered
        // In this case, we don't add anything to the result

        return result;
    }

    /**
     * Checks if a face intersects with a box.
     * This is a simple AABB intersection check.
     * @param face the {@link QuadFace} to check
     * @param box the {@link BoxData} to check against
     * @return true if the face intersects with the box, false otherwise
     */
    private static boolean doIntersect(QuadFace face, BoxData box) {
        // Simple Box intersection check
        return !(face.maxX <= box.minX || face.minX >= box.maxX ||
                face.maxY <= box.minY || face.minY >= box.maxY ||
                face.maxZ <= box.minZ || face.minZ >= box.maxZ);
    }

    /**
     * Calculates the center of all boxes in the {@link #locations} list.
     * @return a {@link GlobalPos} representing the center of all boxes, or null if there are no boxes
     */
    public static GlobalPos getCenterOfAllBoxes() {
        if (locations.isEmpty()) {
            return null;
        }

        double sumX = 0;
        double sumY = 0;
        double sumZ = 0;
        RegistryKey<World> dimension = null;

        for (GlobalPos pos : locations) {
            sumX += pos.pos().getX();
            sumY += pos.pos().getY();
            sumZ += pos.pos().getZ();
            dimension = pos.dimension();
        }

        int centerX = (int) Math.round(sumX / locations.size());
        int centerY = (int) Math.round(sumY / locations.size());
        int centerZ = (int) Math.round(sumZ / locations.size());

        return GlobalPos.create(dimension, new BlockPos(centerX, centerY, centerZ));
    }

    /**
     * Extracts edges from a set of {@link QuadFace} objects.
     * @param faces the set of {@link QuadFace} objects to extract edges from
     * @return a set of {@link Edge} objects representing the edges of the faces
     */
    private static Set<Edge> extractEdgesFromFaces(Set<QuadFace> faces) {
        Set<Edge> edges = new HashSet<>();

        for (QuadFace face : faces) {
            switch (face.direction) {
                case UP:
                case DOWN:
                    // Add the 4 horizontal edges of this face
                    edges.add(new Edge(face.minX, face.minY, face.minZ, face.maxX, face.minY, face.minZ));
                    edges.add(new Edge(face.minX, face.minY, face.maxZ, face.maxX, face.minY, face.maxZ));
                    edges.add(new Edge(face.minX, face.minY, face.minZ, face.minX, face.minY, face.maxZ));
                    edges.add(new Edge(face.maxX, face.minY, face.minZ, face.maxX, face.minY, face.maxZ));
                    break;

                case EAST:
                case WEST:
                    // Add the 4 vertical edges of this face
                    edges.add(new Edge(face.minX, face.minY, face.minZ, face.minX, face.maxY, face.minZ));
                    edges.add(new Edge(face.minX, face.minY, face.maxZ, face.minX, face.maxY, face.maxZ));
                    edges.add(new Edge(face.minX, face.minY, face.minZ, face.minX, face.minY, face.maxZ));
                    edges.add(new Edge(face.minX, face.maxY, face.minZ, face.minX, face.maxY, face.maxZ));
                    break;

                case NORTH:
                case SOUTH:
                    // Add the 4 edges of this face
                    edges.add(new Edge(face.minX, face.minY, face.minZ, face.maxX, face.minY, face.minZ));
                    edges.add(new Edge(face.minX, face.maxY, face.minZ, face.maxX, face.maxY, face.minZ));
                    edges.add(new Edge(face.minX, face.minY, face.minZ, face.minX, face.maxY, face.minZ));
                    edges.add(new Edge(face.maxX, face.minY, face.minZ, face.maxX, face.maxY, face.minZ));
                    break;
            }
        }

        return edges;
    }

    /**
     * Checks if a point is inside any box in a given group.
     * @param x the x-coordinate of the point
     * @param y the y-coordinate of the point
     * @param z the z-coordinate of the point
     * @param boxGroup the set of {@link BoxData} representing the group of boxes to check against
     * @return true if the point is inside any box, false otherwise
     */
    private static boolean isPointInsideAnyBox(float x, float y, float z, Set<BoxData> boxGroup) {
        for (BoxData box : boxGroup) {
            if (x >= box.minX && x <= box.maxX &&
                    y >= box.minY && y <= box.maxY &&
                    z >= box.minZ && z <= box.maxZ) {
                return true;
            }
        }
        return false;
    }

    /*
    -------------------------------------------------------------------
      ____                       _                 _
     |  _ \    ___   _ __     __| |   ___   _ __  (_)  _ __     __ _
     | |_) |  / _ \ | '_ \   / _` |  / _ \ | '__| | | | '_ \   / _` |
     |  _ <  |  __/ | | | | | (_| | |  __/ | |    | | | | | | | (_| |
     |_| \_\  \___| |_| |_|  \__,_|  \___| |_|    |_| |_| |_|  \__, |
                                                               |___/
    -------------------------------------------------------------------
     */

    /**
     * Gets the color for rendering based on the specified color mode and speed.
     * This method returns an array of RGBA values, where each value is in the range [0, 1].
     * @param colorMode the mode of color animation (0-11)
     * @param speed the speed of the color animation
     * @return an array of floats representing RGBA values
     */
    public static float[] getColor(int colorMode, float speed) {
        speed = Math.max(0.01f, Math.min(1f, speed));

        float cycleLength = 1000L / speed;
        float time = (System.currentTimeMillis() % (long) cycleLength) / cycleLength;

        float r = 0, g = 0, b = 0, a = 1F;

        switch (colorMode) {
            case 0: // Smooth Red <-> Green, 3/4 red, 1/4 green
                float t;
                if (time < 0.75f) {
                    t = time / 0.75f;        // scale [0,0.75] → [0,1]
                    r = 1f - 0.5f * (float)(1 - Math.cos(t * Math.PI)); // 1 → 0.5
                    g = 0.5f * (float)(1 - Math.cos(t * Math.PI));      // 0 → 0.5
                } else {
                    t = (time - 0.75f) / 0.25f; // scale [0.75,1] → [0,1]
                    g = 1f - 0.5f * (float)(1 - Math.cos(t * Math.PI)); // 1 → 0.5
                    r = 0.5f * (float)(1 - Math.cos(t * Math.PI));      // 0 → 0.5
                }
                b = 0;
                break;

            case 1: // Pulsating Red
                r = 1f - (float)Math.abs(Math.sin(time * 2 * Math.PI));
                g = 0;
                b = 0;
                break;
            case 2: // Pulsating Green
                r = 0;
                g = 1f - (float)Math.abs(Math.sin(time * 2 * Math.PI));
                b = 0;
                break;
            case 3: // Pulsating Blue
                r = 0;
                g = 0;
                b = 1f - (float)Math.abs(Math.sin(time * 2 * Math.PI));
                break;

            case 4: // Smooth RGB rainbow (fixed)
                r = 0.5f + 0.5f * (float)Math.sin(time * 2 * Math.PI);
                g = 0.5f + 0.5f * (float)Math.sin(time * 2 * Math.PI + 2 * Math.PI / 3);
                b = 0.5f + 0.5f * (float)Math.sin(time * 2 * Math.PI + 4 * Math.PI / 3);
                break;

            case 5: // Static Yellow
                r = 1.0F;
                g = 1.0F;
                b = 0.0F;
                break;
            case 6: // Static Cyan
                r = 0.0F;
                g = 1.0F;
                b = 1.0F;
                break;
            case 7: // Static Magenta
                r = 1.0F;
                g = 0.0F;
                b = 1.0F;
                break;
            case 8: // Alternating Red/Green
                r = (time % 1.0F) < 0.5F ? 1.0F : 0.0F;
                g = (time % 1.0F) >= 0.5F ? 1.0F : 0.0F;
                b = 0.0F;
                break;
            case 9: // Random Colors
                r = (float)Math.random();
                g = (float)Math.random();
                b = (float)Math.random();
                break;
            case 10: // Sinusoidal RGB (phase-shifted)
                r = 0.5f + 0.5f * (float)Math.sin(time * 2 * Math.PI);
                g = 0.5f + 0.5f * (float)Math.sin(time * 2 * Math.PI + 2 * Math.PI / 3);
                b = 0.5f + 0.5f * (float)Math.sin(time * 2 * Math.PI + 4 * Math.PI / 3);
                break;
            case 11: // Sinusoidal Blue <-> Cyan
                // Blue = (0,0,1), Cyan = (0,1,1)
                float mix = 0.5f + 0.5f * (float)Math.sin(time * 2 * Math.PI);
                r = 0;
                g = mix;
                b = 1;
                break;

            default: // Default to white
                r = 1.0F;
                g = 1.0F;
                b = 1.0F;
                break;
        }

        // Clamp values to [0, 1]
        r = Math.max(0, Math.min(1, r));
        g = Math.max(0, Math.min(1, g));
        b = Math.max(0, Math.min(1, b));

        return new float[]{r, g, b, a};
    }

    /**
     * Renders filled faces for a set of {@link QuadFace} objects.
     * @param poseStack the {@link MatrixStack} used for rendering
     * @param faces the set of {@link QuadFace} objects to render as filled faces
     */
    private static void renderFilledFaces(MatrixStack poseStack, Set<QuadFace> faces) {
        MinecraftClient minecraft = MinecraftClient.getInstance();
        if (minecraft.world == null) return;

        VertexConsumerProvider.Immediate bufferSource = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
        int packedLight = LightmapTextureManager.pack(15, 15);

        Identifier texture = Identifier.of(MOD_ID, "textures/block/outline.png");
        RenderLayer renderType = CustomRenderLayers.translucent(texture);
        VertexConsumer vertexConsumer = bufferSource.getBuffer(renderType);

        float[] color = getColor(11, 0.25F); // Mode 0 with speed 0.25
        float r = color[0];
        float g = color[1];
        float b = color[2];
        float a = color[3];

        Matrix4f matrix = poseStack.peek().getPositionMatrix();

        for (QuadFace face : faces) {
            drawQuad(vertexConsumer, matrix, face.minX, face.minY, face.minZ,
                    face.maxX, face.maxY, face.maxZ, r, g, b, a, packedLight, 0, 0, 1, 1, face.direction);
        }

        bufferSource.draw(renderType);
    }

    /**
     * Renders the outline of a group of boxes.
     * @param poseStack the {@link MatrixStack} used for rendering
     * @param boxGroup the set of {@link BoxData} representing the group of boxes to render the outline for
     */
    private static void renderGroupOutline(MatrixStack poseStack, Set<BoxData> boxGroup) {
        RenderSystem.lineWidth(LINE_WIDTH_FULL);

        Set<QuadFace> exteriorFaces = calculateExteriorFaces(boxGroup);

        for(QuadFace face : exteriorFaces) {
            renderBoxOutline(poseStack, (int) face.minX, (int) face.minY, (int) face.minZ, (int) face.maxX, (int) face.maxY, (int) face.maxZ, false, 11);
        }

        MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers().draw(RenderLayer.getLines());
    }

    @Deprecated
    private static void drawLine(VertexConsumer builder, Matrix4f matrix, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b, float a) {
        builder.vertex(matrix, x1, y1, z1)
                .color(r, g, b, a)
                .normal(0, 1, 0);
        builder.vertex(matrix, x2, y2, z2)
                .color(r, g, b, a)
                .normal(0, 1, 0);
    }


    /**
     * Main method to render the player's selection box. Renders the outline and filled box
     * @param matrixStack the {@link MatrixStack} used for rendering
     * @param firstCorner the first corner of the selection box
     * @param secondCorner the second corner of the selection box
     * @param cameraPos the current camera position in the world
     */
    public static void renderBox(MatrixStack matrixStack, BlockPos firstCorner, BlockPos secondCorner, Vec3d cameraPos) {
        if (firstCorner == null || secondCorner == null) return;

        // Create the bounding box
        int minX = Math.min(firstCorner.getX(), secondCorner.getX());
        int minY = Math.min(firstCorner.getY(), secondCorner.getY());
        int minZ = Math.min(firstCorner.getZ(), secondCorner.getZ());
        int maxX = Math.max(firstCorner.getX(), secondCorner.getX()) + 1;
        int maxY = Math.max(firstCorner.getY(), secondCorner.getY()) + 1;
        int maxZ = Math.max(firstCorner.getZ(), secondCorner.getZ()) + 1;

        // Render box lines and faces
        matrixStack.push();
        // Adjust for camera position
        matrixStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        // Draw filled box (semi-transparent) and outline
        renderFilledBox(matrixStack, minX, minY, minZ, maxX, maxY, maxZ);
        renderBoxOutline(matrixStack, minX, minY, minZ, maxX, maxY, maxZ, true, 0);

        matrixStack.pop();
    }

    /**
     * Renders all connected boxes owned by the client.
     * This method finds connected groups of boxes and renders them as filled boxes with outlines
     * @param poseStack the {@link MatrixStack} used for rendering
     * @param cameraPos the current camera position in the world
     */
    public static void renderConnectedBoxes(MatrixStack poseStack, Vec3d cameraPos) {
        if (connectedBoxes.isEmpty()) return;

        // Find connected groups of boxes
        List<Set<BoxData>> connectedGroups = findConnectedGroups();

        poseStack.push();
        // Adjust for camera position
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        // Render each connected group
        for (Set<BoxData> group : connectedGroups) {
            renderBoxGroup(poseStack, group);
        }

        poseStack.pop();
    }

    /**
     * Renders all connected boxes owned by other players.
     * This method finds connected groups of boxes for each player and renders them as filled boxes with outlines
     * @param poseStack the {@link MatrixStack} used for rendering
     * @param cameraPos the current camera position in the world
     */
    public static void renderOtherPeoplesConnectedBoxes(MatrixStack poseStack, Vec3d cameraPos) {
        if (otherPeoplesConnectedBoxes.isEmpty()) return;
        for (UUID playerName : otherPeoplesConnectedBoxes.keySet()) {
            List<BoxData> boxDataList = otherPeoplesConnectedBoxes.get(playerName);
            if (boxDataList.isEmpty()) continue;
            // Find connected groups of boxes
            List<Set<BoxData>> connectedGroups = findOtherPeoplesConnectedGroups(playerName);

            poseStack.push();
            // Adjust for camera position
            poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

            // Render each connected group
            for (Set<BoxData> group : connectedGroups) {
                renderBoxGroup(poseStack, group);
            }

            poseStack.pop();
        }
    }

    /**
     * Internal method to render a group of boxes, while separating the logic for rendering filled faces and outlines.
     * Some merging of faces happens here too (it's janky tho)
     * @param poseStack the {@link MatrixStack} used for rendering
     * @param boxGroup  the set of {@link BoxData} representing the group of boxes to render
     */
    private static void renderBoxGroup(MatrixStack poseStack, Set<BoxData> boxGroup) {
        // Calculate the faces that need to be rendered (exterior only)
        Set<QuadFace> faces = calculateExteriorFaces(boxGroup);

        // Render filled faces and outline
        renderFilledFaces(poseStack, faces);
        renderGroupOutline(poseStack, boxGroup);
    }

    /**
     * Helper method to render a filled box with a translucent texture.
     * @param poseStack the {@link MatrixStack} used for rendering
     * @param minX the x-coordinate of the first corner/position of the box
     * @param minY the y-coordinate of the first corner/position of the box
     * @param minZ the z-coordinate of the first corner/position of the box
     * @param maxX the x-coordinate of the second corner/position of the box
     * @param maxY the y-coordinate of the second corner/position of the box
     * @param maxZ the z-coordinate of the second corner/position of the box
     */
    private static void renderFilledBox(MatrixStack poseStack, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        MinecraftClient minecraft = MinecraftClient.getInstance();
        if (minecraft.world == null) return;

        VertexConsumerProvider.Immediate bufferSource = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();

        // Get light World for the area
        BlockPos centerPos = new BlockPos((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2);

        int packedLight = LightmapTextureManager.pack(15, 15);

        // Create a translucent filled box
        Identifier texture = Identifier.of(MOD_ID, "textures/block/outline.png");
        RenderLayer renderType = CustomRenderLayers.translucent(texture);
        VertexConsumer vertexConsumer = bufferSource.getBuffer(renderType);

        // The color for the filled box (blue with transparency)
        float[] color = getColor(0, 0.25F); // Mode 0 with speed 0.25
        float r = color[0];
        float g = color[1];
        float b = color[2];
        float a = color[3];

        Matrix4f matrix = poseStack.peek().getPositionMatrix();

        // Draw all faces
        drawQuad(vertexConsumer, matrix, minX, minY, minZ, maxX, maxY, maxZ, r, g, b, a, packedLight, 0, 0, 1, 1, Direction.NORTH); // Front
        drawQuad(vertexConsumer, matrix, minX, minY, minZ, maxX, maxY, maxZ, r, g, b, a, packedLight, 0, 0, 1, 1, Direction.SOUTH); // Back
        drawQuad(vertexConsumer, matrix, minX, minY, minZ, maxX, maxY, maxZ, r, g, b, a, packedLight, 0, 0, 1, 1, Direction.DOWN);  // Bottom
        drawQuad(vertexConsumer, matrix, minX, minY, minZ, maxX, maxY, maxZ, r, g, b, a, packedLight, 0, 0, 1, 1, Direction.UP);    // Top
        drawQuad(vertexConsumer, matrix, minX, minY, minZ, maxX, maxY, maxZ, r, g, b, a, packedLight, 0, 0, 1, 1, Direction.WEST);  // Left
        drawQuad(vertexConsumer, matrix, minX, minY, minZ, maxX, maxY, maxZ, r, g, b, a, packedLight, 0, 0, 1, 1, Direction.EAST);  // Right
    }

    /**
     * Internal helper method to draw a quad face with the specified parameters.
     * @param vertexConsumer the {@link VertexConsumer} to use for rendering
     * @param matrix the {@link Matrix4f} transformation matrix
     * @param minX the x-coordinate of the first corner/position of the quad
     * @param minY the y-coordinate of the first corner/position of the quad
     * @param minZ the z-coordinate of the first corner/position of the quad
     * @param maxX the x-coordinate of the second corner/position of the quad
     * @param maxY the y-coordinate of the second corner/position of the quad
     * @param maxZ the z-coordinate of the second corner/position of the quad
     * @param r the red value of the color of the quad (0-1)
     * @param g the green value of the color of the quad (0-1)
     * @param b the blue value of the color of the quad (0-1)
     * @param a the alpha value of the color of the quad (0-1)
     * @param packedLight the packed light value for the quad
     * @param u0 the u-coordinate of the first texture coordinate
     * @param v0 the v-coordinate of the first texture coordinate
     * @param u1 the u-coordinate of the second texture coordinate
     * @param v1 the v-coordinate of the second texture coordinate
     * @param face the {@link Direction} of the face to draw
     */
    public static void drawQuad(VertexConsumer vertexConsumer, Matrix4f matrix, float minX, float minY, float minZ, float maxX, float maxY, float maxZ, float r, float g, float b, float a, int packedLight, float u0, float v0, float u1, float v1, Direction face) {
        int normalX = 0, normalY = 0, normalZ = 0;
        float faceR = r, faceG = g, faceB = b, faceA = a;

        boolean useDebugColors = false;
        if(useDebugColors) {
            switch (face) {
                case NORTH: // Front face (Z-)
                    faceR = 0f;
                    faceG = 0f;
                    faceB = 1f; // Blue
                    break;
                case SOUTH: // Back face (Z+)
                    faceR = 1f;
                    faceG = 0.5f;
                    faceB = 0f; // Orange
                    break;
                case DOWN: // Bottom face (Y-)
                    faceR = 0f;
                    faceG = 1f;
                    faceB = 0f; // Green
                    break;
                case UP: // Top face (Y+)
                    faceR = 1f;
                    faceG = 1f;
                    faceB = 0f; // Yellow
                    break;
                case WEST: // Left face (X-)
                    faceR = 1f;
                    faceG = 0f;
                    faceB = 0f; // Red
                    break;
                case EAST: // Right face (X+)
                    faceR = 0.8f;
                    faceG = 0f;
                    faceB = 1f; // Purple
                    break;
            }
        }

        switch (face) {
            case NORTH: // Front face (Z-)
                vertexConsumer.vertex(matrix, minX, minY, minZ).color(faceR, faceG, faceB, faceA).texture(u0, v0).overlay(OverlayTexture.DEFAULT_UV).light(packedLight).normal(normalX, normalY, normalZ);
                vertexConsumer.vertex(matrix, minX, maxY, minZ).color(faceR, faceG, faceB, faceA).texture(u0, v1).overlay(OverlayTexture.DEFAULT_UV).light(packedLight).normal(normalX, normalY, normalZ);
                vertexConsumer.vertex(matrix, maxX, maxY, minZ).color(faceR, faceG, faceB, faceA).texture(u1, v1).overlay(OverlayTexture.DEFAULT_UV).light(packedLight).normal(normalX, normalY, normalZ);
                vertexConsumer.vertex(matrix, maxX, minY, minZ).color(faceR, faceG, faceB, faceA).texture(u1, v0).overlay(OverlayTexture.DEFAULT_UV).light(packedLight).normal(normalX, normalY, normalZ);
                break;
            case SOUTH: // Back face (Z+)
                vertexConsumer.vertex(matrix, maxX, minY, maxZ).color(faceR, faceG, faceB, faceA).texture(u1, v0).overlay(OverlayTexture.DEFAULT_UV).light(packedLight).normal(normalX, normalY, normalZ);
                vertexConsumer.vertex(matrix, maxX, maxY, maxZ).color(faceR, faceG, faceB, faceA).texture(u1, v1).overlay(OverlayTexture.DEFAULT_UV).light(packedLight).normal(normalX, normalY, normalZ);
                vertexConsumer.vertex(matrix, minX, maxY, maxZ).color(faceR, faceG, faceB, faceA).texture(u0, v1).overlay(OverlayTexture.DEFAULT_UV).light(packedLight).normal(normalX, normalY, normalZ);
                vertexConsumer.vertex(matrix, minX, minY, maxZ).color(faceR, faceG, faceB, faceA).texture(u0, v0).overlay(OverlayTexture.DEFAULT_UV).light(packedLight).normal(normalX, normalY, normalZ);
                break;
            case DOWN: // Bottom face (Y-)
                vertexConsumer.vertex(matrix, minX, minY, maxZ).color(faceR, faceG, faceB, faceA).texture(u0, v1).overlay(OverlayTexture.DEFAULT_UV).light(packedLight).normal(normalX, normalY, normalZ);
                vertexConsumer.vertex(matrix, maxX, minY, maxZ).color(faceR, faceG, faceB, faceA).texture(u1, v1).overlay(OverlayTexture.DEFAULT_UV).light(packedLight).normal(normalX, normalY, normalZ);
                vertexConsumer.vertex(matrix, maxX, minY, minZ).color(faceR, faceG, faceB, faceA).texture(u1, v0).overlay(OverlayTexture.DEFAULT_UV).light(packedLight).normal(normalX, normalY, normalZ);
                vertexConsumer.vertex(matrix, minX, minY, minZ).color(faceR, faceG, faceB, faceA).texture(u0, v0).overlay(OverlayTexture.DEFAULT_UV).light(packedLight).normal(normalX, normalY, normalZ);
                break;
            case UP: // Top face (Y+)
                vertexConsumer.vertex(matrix, minX, maxY, minZ).color(faceR, faceG, faceB, faceA).texture(u0, v0).overlay(OverlayTexture.DEFAULT_UV).light(packedLight).normal(normalX, normalY, normalZ);
                vertexConsumer.vertex(matrix, maxX, maxY, minZ).color(faceR, faceG, faceB, faceA).texture(u1, v0).overlay(OverlayTexture.DEFAULT_UV).light(packedLight).normal(normalX, normalY, normalZ);
                vertexConsumer.vertex(matrix, maxX, maxY, maxZ).color(faceR, faceG, faceB, faceA).texture(u1, v1).overlay(OverlayTexture.DEFAULT_UV).light(packedLight).normal(normalX, normalY, normalZ);
                vertexConsumer.vertex(matrix, minX, maxY, maxZ).color(faceR, faceG, faceB, faceA).texture(u0, v1).overlay(OverlayTexture.DEFAULT_UV).light(packedLight).normal(normalX, normalY, normalZ);
                break;
            case WEST: // Left face (X-)
                vertexConsumer.vertex(matrix, minX, minY, maxZ).color(faceR, faceG, faceB, faceA).texture(u0, v1).overlay(OverlayTexture.DEFAULT_UV).light(packedLight).normal(normalX, normalY, normalZ);
                vertexConsumer.vertex(matrix, minX, maxY, maxZ).color(faceR, faceG, faceB, faceA).texture(u1, v1).overlay(OverlayTexture.DEFAULT_UV).light(packedLight).normal(normalX, normalY, normalZ);
                vertexConsumer.vertex(matrix, minX, maxY, minZ).color(faceR, faceG, faceB, faceA).texture(u1, v0).overlay(OverlayTexture.DEFAULT_UV).light(packedLight).normal(normalX, normalY, normalZ);
                vertexConsumer.vertex(matrix, minX, minY, minZ).color(faceR, faceG, faceB, faceA).texture(u0, v0).overlay(OverlayTexture.DEFAULT_UV).light(packedLight).normal(normalX, normalY, normalZ);
                break;
            case EAST: // Right face (X+)
                vertexConsumer.vertex(matrix, maxX, minY, minZ).color(faceR, faceG, faceB, faceA).texture(u0, v0).overlay(OverlayTexture.DEFAULT_UV).light(packedLight).normal(normalX, normalY, normalZ);
                vertexConsumer.vertex(matrix, maxX, maxY, minZ).color(faceR, faceG, faceB, faceA).texture(u1, v0).overlay(OverlayTexture.DEFAULT_UV).light(packedLight).normal(normalX, normalY, normalZ);
                vertexConsumer.vertex(matrix, maxX, maxY, maxZ).color(faceR, faceG, faceB, faceA).texture(u1, v1).overlay(OverlayTexture.DEFAULT_UV).light(packedLight).normal(normalX, normalY, normalZ);
                vertexConsumer.vertex(matrix, maxX, minY, maxZ).color(faceR, faceG, faceB, faceA).texture(u0, v1).overlay(OverlayTexture.DEFAULT_UV).light(packedLight).normal(normalX, normalY, normalZ);
                break;
        }
    }

    /**
     * Renders the lines on a box face. Can also render a white outline around the entire box if specified. Includes a color mode for the face line colors.
     * @param poseStack the {@link MatrixStack} used for rendering
     * @param minX the x-coordinate of the first corner/position of the face
     * @param minY the y-coordinate of the first corner/position of the face
     * @param minZ the z-coordinate of the first corner/position of the face
     * @param maxX the x-coordinate of the second corner/position of the face
     * @param maxY the y-coordinate of the second corner/position of the face
     * @param maxZ the z-coordinate of the second corner/position of the face
     * @param renderWhiteOutline whether to render a white outline around the entire box
     * @param colorMode the color mode for the face lines (0-11)
     */
    private static void renderBoxOutline(MatrixStack poseStack, int minX, int minY, int minZ,
                                         int maxX, int maxY, int maxZ,
                                         boolean renderWhiteOutline, int colorMode) {
        VertexConsumerProvider.Immediate bufferSource = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
        var buffer = bufferSource.getBuffer(RenderLayer.getLines());

        RenderSystem.lineWidth(LINE_WIDTH);

        if (renderWhiteOutline) {
            // Draw the main box outline (fully opaque)
            WorldRenderer.drawBox(
                    poseStack,
                    buffer,
                    minX, minY, minZ,
                    maxX, maxY, maxZ,
                    1.0F, 1.0F, 1.0F, 1.0F
            );
        }

        // Color for interior lines
        float[] color = getColor(colorMode, 0.25F);
        float r = color[0], g = color[1], b = color[2], a = 0.75F;

        int longLines = 10;  // spacing for long direction
        int shortLines = 1;  // spacing for short direction

        // --- NORTH face (Z = minZ) ---
        for (int x = roundToStep(minX, longLines); x < maxX; x += longLines) {
            renderLine(poseStack, buffer, x, minY, minZ, x, maxY, minZ, r, g, b, a);
        }
        for (int y = roundToStep(minY, shortLines); y < maxY; y += shortLines) {
            renderLine(poseStack, buffer, minX, y, minZ, maxX, y, minZ, r, g, b, a);
        }

        // --- SOUTH face (Z = maxZ) ---
        for (int x = roundToStep(minX, longLines); x < maxX; x += longLines) {
            renderLine(poseStack, buffer, x, minY, maxZ, x, maxY, maxZ, r, g, b, a);
        }
        for (int y = roundToStep(minY, shortLines); y < maxY; y += shortLines) {
            renderLine(poseStack, buffer, minX, y, maxZ, maxX, y, maxZ, r, g, b, a);
        }

        // --- EAST face (X = maxX) ---
        for (int y = roundToStep(minY, shortLines); y < maxY; y += shortLines) {
            renderLine(poseStack, buffer, maxX, y, minZ, maxX, y, maxZ, r, g, b, a);
        }
        for (int z = roundToStep(minZ, longLines); z < maxZ; z += longLines) {
            renderLine(poseStack, buffer, maxX, minY, z, maxX, maxY, z, r, g, b, a);
        }

        // --- WEST face (X = minX) ---
        for (int y = roundToStep(minY, shortLines); y < maxY; y += shortLines) {
            renderLine(poseStack, buffer, minX, y, minZ, minX, y, maxZ, r, g, b, a);
        }
        for (int z = roundToStep(minZ, longLines); z < maxZ; z += longLines) {
            renderLine(poseStack, buffer, minX, minY, z, minX, maxY, z, r, g, b, a);
        }

        // --- UP face (Y = maxY) ---
        for (int x = roundToStep(minX, longLines); x < maxX; x += longLines) {
            renderLine(poseStack, buffer, x, maxY, minZ, x, maxY, maxZ, r, g, b, a);
        }
        for (int z = roundToStep(minZ, shortLines); z < maxZ; z += shortLines) {
            renderLine(poseStack, buffer, minX, maxY, z, maxX, maxY, z, r, g, b, a);
        }

        // --- DOWN face (Y = minY) ---
        for (int x = roundToStep(minX, longLines); x < maxX; x += longLines) {
            renderLine(poseStack, buffer, x, minY, minZ, x, minY, maxZ, r, g, b, a);
        }
        for (int z = roundToStep(minZ, shortLines); z < maxZ; z += shortLines) {
            renderLine(poseStack, buffer, minX, minY, z, maxX, minY, z, r, g, b, a);
        }

        bufferSource.draw(RenderLayer.getLines());
    }

    /**
     * Aligns the starting coordinate to the nearest grid step relative to world origin (0,0,0).
     * @param coord the coordinate to align
     * @param step the step size to align to
     */
    private static int roundToStep(int coord, int step) {
        int aligned = (int)Math.ceil((double)coord / step) * step;
        return aligned;
    }


    /**
     * Renders a line between two points in 3D space.
     * @param poseStack the {@link MatrixStack} used for rendering
     * @param consumer the {@link VertexConsumer} to use for rendering the line
     * @param minX the x-coordinate of the first point
     * @param minY the y-coordinate of the first point
     * @param minZ the z-coordinate of the first point
     * @param maxX the x-coordinate of the second point
     * @param maxY the y-coordinate of the second point
     * @param maxZ the z-coordinate of the second point
     * @param red the red value of the color of the line
     * @param green the green value of the color of the line
     * @param blue the blue value of the color of the line
     * @param alpha the alpha value of the color of the line
     */
    public static void renderLine(MatrixStack poseStack, VertexConsumer consumer, double minX, double minY, double minZ, double maxX, double maxY, double maxZ, float red, float green, float blue, float alpha) {
        MatrixStack.Entry pose = poseStack.peek();

        float x1 = (float) minX;
        float y1 = (float) minY;
        float z1 = (float) minZ;
        float x2 = (float) maxX;
        float y2 = (float) maxY;
        float z2 = (float) maxZ;

        // First point of the line
        consumer.vertex(pose, x1, y1, z1).color(red, green, blue, alpha).normal(pose, 0.0F, 1.0F, 0.0F);

        // Second point of the line
        consumer.vertex(pose, x2, y2, z2).color(red, green, blue, alpha).normal(pose, 0.0F, 1.0F, 0.0F);
    }

    /**
     * Renders a highlight outline around a block at the specified position with specific colors.
     * @param poseStack the {@link MatrixStack} used for rendering
     * @param pos the {@link BlockPos} of the block to highlight
     * @param cameraPos the current camera position in the world
     * @param r the red value of the highlight color (0-1)
     * @param g the green value of the highlight color (0-1)
     * @param b the blue value of the highlight color (0-1)
     * @param a the alpha value of the highlight color (0-1)
     */
    public static void renderBlockHighlight(MatrixStack poseStack, BlockPos pos, Vec3d cameraPos, float r, float g, float b, float a) {
        MinecraftClient minecraft = MinecraftClient.getInstance();
        if (minecraft.world == null || pos == null) return;

        poseStack.push();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        VertexConsumerProvider.Immediate bufferSource = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer consumer = bufferSource.getBuffer(RenderLayer.getLines());

        // Slightly larger than the block to avoid z-fighting
        float expansion = 0.002f;

        // Draw the box outline
        WorldRenderer.drawBox(
                poseStack,
                consumer,
                pos.getX() - expansion,
                pos.getY() - expansion,
                pos.getZ() - expansion,
                pos.getX() + 1 + expansion,
                pos.getY() + 1 + expansion,
                pos.getZ() + 1 + expansion,
                r, g, b, a
        );

        bufferSource.draw(RenderLayer.getLines());
        poseStack.pop();
    }
}
