package config;

import java.util.List;

public record GameConfig(
        String levelName,
        List<SystemConfig> systems
) {

    /** One system box on the canvas */
    public static record SystemConfig(
            int id,
            String type,
            Position position,
            List<String> inputPorts,
            List<String> outputPorts,
            List<PacketConfig> initialPackets
    ) {
    }

    /** x / y top‑left anchor */
    public static record Position(int x, int y) { }

    /** Spawn instruction for packets inside a system */
    public static record PacketConfig(
            int packetId,
            String type,
            int count,
            int colorId
    ) { }
}
