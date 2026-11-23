package com.mojang.minecraft.level.tile;

import com.mojang.minecraft.level.Level;

public class LogTile extends Tile {

    /**
     * Create a log tile with the id
     *
     * @param id The id of the log tile
     */
    protected LogTile(int id) {
        super(id);
        this.textureId = 20; // Wood side texture
    }

    @Override
    protected int getTexture(int face) {
        // Top and bottom faces use wood top texture (ring pattern)
        // Side faces use wood side texture (bark pattern)
        if (face == 0 || face == 1) {
            return 21; // Wood top
        }
        return 20; // Wood side
    }
}
