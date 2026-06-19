package com.qdrppl.newbridge.Utils;

import net.minecraft.core.BlockPos;

public class PathNode implements Comparable<PathNode> {
    public BlockPos pos;
    public PathNode parent;
    public double gCost;
    public double hCost;
    public double fCost;

    public PathNode(BlockPos pos, PathNode parent, double gCost, double hCost) {
        this.pos = pos;
        this.parent = parent;
        this.gCost = gCost;
        this.hCost = hCost;
        this.fCost = gCost + hCost;
    }

    @Override
    public int compareTo(PathNode o) {
        return Double.compare(this.fCost, o.fCost);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof PathNode) {
            return ((PathNode) obj).pos.equals(this.pos);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return pos.hashCode();
    }
}