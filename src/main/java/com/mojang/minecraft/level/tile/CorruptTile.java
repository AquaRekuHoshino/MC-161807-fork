package com.mojang.minecraft.level.tile;

import com.mojang.minecraft.level.Level;
import com.mojang.minecraft.level.Tessellator;

import java.util.Random;

public class CorruptTile extends Tile {

    private int corruptionLevel = 0;
    public static boolean corruptionTriggered = false;
    public static int corruptionTimer = 0;
    public static final int CORRUPTION_DELAY = 400; // 20 segundos (20 ticks por segundo)

    /**
     * Create a corrupt tile with the id
     *
     * @param id The id of the corrupt tile
     */
    protected CorruptTile(int id) {
        super(id);
    }

    @Override
    public void render(Tessellator tessellator, Level level, int layer, int x, int y, int z) {
        // Render black without texture
        tessellator.color(0.0f, 0.0f, 0.0f);

        // Render all faces without texture
        renderFaceNoTexture(tessellator, x, y, z, 0); // bottom
        renderFaceNoTexture(tessellator, x, y, z, 1); // top
        renderFaceNoTexture(tessellator, x, y, z, 2); // front
        renderFaceNoTexture(tessellator, x, y, z, 3); // back
        renderFaceNoTexture(tessellator, x, y, z, 4); // left
        renderFaceNoTexture(tessellator, x, y, z, 5); // right
    }

    @Override
    public void onTick(Level level, int x, int y, int z, Random random) {
        corruptionLevel++;

        // Spread corruption to nearby blocks
        if (corruptionLevel % 5 == 0) {
            int radius = 3 + corruptionLevel / 10;

            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (random.nextInt(20) == 0) {
                            int targetX = x + dx;
                            int targetY = y + dy;
                            int targetZ = z + dz;

                            int currentTile = level.getTile(targetX, targetY, targetZ);
                            if (currentTile != 0 && currentTile != this.id) {
                                // Corrupt: turn to random tile or corrupt tile
                                if (random.nextInt(10) == 0) {
                                    level.setTile(targetX, targetY, targetZ, this.id);
                                } else {
                                    level.setTile(targetX, targetY, targetZ, Tile.rock.id);
                                }
                            }
                        }
                    }
                }
            }
        }

        // Extreme corruption: crash the game eventually
        if (corruptionLevel > 100) {
            // Create chaos by setting random blocks everywhere
            for (int i = 0; i < 100; i++) {
                int rx = random.nextInt(level.width);
                int ry = random.nextInt(level.depth);
                int rz = random.nextInt(level.height);
                level.setTile(rx, ry, rz, random.nextInt(6) + 1);
            }

            // Eventually cause stack overflow
            if (corruptionLevel > 200) {
                recursiveCorruption(level, random, 0);
            }
        }
    }

    private void recursiveCorruption(Level level, Random random, int depth) {
        if (depth > 100) return;
        
        // Random block changes
        int x = random.nextInt(level.width);
        int y = random.nextInt(level.depth);
        int z = random.nextInt(level.height);
        level.setTile(x, y, z, random.nextInt(7));
        
        recursiveCorruption(level, random, depth + 1);
    }

    @Override
    public boolean isSolid() {
        return true;
    }
}
