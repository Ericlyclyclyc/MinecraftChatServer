package org.lyc122.dev.minecraftchatserver.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 位置坐标（用于区域广播）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Position {
    private double x;
    private double y;
    private double z;
    private String world;
    
    public Position(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.world = "world";
    }
    
    /**
     * 计算到另一个位置的距离
     */
    public double distanceTo(Position other) {
        if (other == null || !this.world.equals(other.world)) {
            return Double.MAX_VALUE;
        }
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        double dz = this.z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
