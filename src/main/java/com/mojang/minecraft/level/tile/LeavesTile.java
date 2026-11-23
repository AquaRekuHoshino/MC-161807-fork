package com.mojang.minecraft.level.tile;

import com.mojang.minecraft.level.Level;

public class LeavesTile extends Tile {

    /**
     * Create a leaves tile with the id
     *
     * @param id The id of the leaves tile
     */
    protected LeavesTile(int id) {
        super(id);
        this.textureId = 52; // Leaves texture
    }

    @Override
    public boolean isSolid() {
        return false; // Leaves are not solid (can walk through)
    }

    @Override
    public boolean blocksLight() {
        return false; // Leaves let light through
    }
}
