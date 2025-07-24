// src/main/java/model/LevelsManager.java
package model;

import config.ConfigManager;
import config.GameConfig;
import config.GameConfig.SystemConfig;
import config.GameConfig.PacketConfig;
import model.ports.InputPort;
import model.ports.OutputPort;
import model.packets.*;
import model.ports.inputs.*;
import model.ports.outputs.*;
import model.systems.*;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Manages all game levels by creating a SystemManager for each configured level.
 */
public class LevelsManager {
    private final List<GameConfig> configs;
    private final List<SystemManager> levelManagers;

    public LevelsManager() {
        // Load all level configurations
        this.configs = ConfigManager.getInstance().getAllLevels();
        this.levelManagers = new ArrayList<>(configs.size());

        // Build a SystemManager for each level
        for (GameConfig cfg : configs) {
            SystemManager sm = new SystemManager();

            // For each system defined in this level
            for (SystemConfig sc : cfg.systems()) {
                // Prepare empty port lists
                List<InputPort> inputPorts = new ArrayList<>();
                List<OutputPort> outputPorts = new ArrayList<>();

                // System location
                Point loc = new Point(sc.position().x(), sc.position().y());

                // Instantiate the correct System subtype
                model.System sys = switch (sc.type()) {
                    case "ReferenceSystem"    -> new ReferenceSystem(loc, inputPorts, outputPorts, sm, sc.id());
                    case "NormalSystem"       -> new NormalSystem    (loc, inputPorts, outputPorts, sm, sc.id());
                    case "SpySystem"          -> new SpySystem       (loc, inputPorts, outputPorts, sm, sc.id());
                    case "VpnSystem"          -> new VpnSystem       (loc, inputPorts, outputPorts, sm, sc.id());
                    case "AntiTrojanSystem"   -> new AntiTrojanSystem(loc, inputPorts, outputPorts, sm, sc.id());
                    case "DestroyerSystem"    -> new DestroyerSystem (loc, inputPorts, outputPorts, sm, sc.id());
                    case "DistributionSystem" -> new DistributionSystem(loc, inputPorts, outputPorts, sm, sc.id());
                    case "MergerSystem"       -> new MergerSystem    (loc, inputPorts, outputPorts, sm, sc.id());
                    default -> new ReferenceSystem(loc, inputPorts, outputPorts, sm, sc.id());
                };

                // Create and collect InputPort instances at placeholder positions
                // system top‐left corner
                // System top‐left corner
                int sysX = sc.position().x();
                int sysY = sc.position().y();

// System dimensions (must match your SystemView.WIDTH / HEIGHT)
                int sysW = view.SystemView.WIDTH;
                int sysH = view.SystemView.HEIGHT;

// 1) INPUTS down the left edge
                List<String> inNames = sc.inputPorts();
                for (int i = 0; i < inNames.size(); i++) {
                    // center x on left border
                    int x = sysX;
                    // evenly spaced along height
                    int y = sysY + (i + 1) * sysH / (inNames.size() + 1);
                    String inName = inNames.get(i);
                    InputPort ip = makeInputPort(sys, new Point(x, y), inName);
                    inputPorts.add(ip);
                }

// 2) OUTPUTS down the right edge
                List<String> outNames = sc.outputPorts();
                for (int i = 0; i < outNames.size(); i++) {
                    // center x on right border
                    int x = sysX + sysW;
                    // same vertical spacing
                    int y = sysY + (i + 1) * sysH / (outNames.size() + 1);
                    String inName = inNames.get(i);
                    OutputPort op = makeOutputPort(sys, new Point(x, y), inName);
                    outputPorts.add(op);
                }

                // Register the system in its manager
                sm.addSystem(sys);

                // Spawn initial packets inside the system
                for (PacketConfig pc : sc.initialPackets()) {
                    for (int i = 0; i < pc.count(); i++) {
                        Packet pkt = switch (pc.type()) {
                            case "SquarePacket"   -> new SquarePacket();
                            case "TrianglePacket" -> new TrianglePacket();
                            case "InfinityPacket" -> new InfinityPacket();
                            default                -> new SquarePacket();
                        };
                        sys.addPacket(pkt);
                        sm.addPacket(pkt);
                    }
                }
            }

            // Store this level's manager
            levelManagers.add(sm);
        }
    }

    public List<GameConfig> getLevelConfigs() {
        return List.copyOf(configs);
    }

    public List<SystemManager> getAllLevelManagers() {
        return List.copyOf(levelManagers);
    }

    public SystemManager getLevelManager(int idx) {
        return levelManagers.get(idx);
    }

    public SystemManager getLevelManager(String levelName) {
        for (int i = 0; i < configs.size(); i++) {
            if (Objects.equals(configs.get(i).levelName(), levelName)) {
                return levelManagers.get(i);
            }
        }
        return null;
    }
    private static InputPort makeInputPort(model.System sys,
                                           Point centre,
                                           String jsonName)
    {
        return switch (jsonName) {
            case "SquarePort"   -> new SquareInput(sys, centre);
            case "TrianglePort" -> new TriangleInput(sys, centre);
            case "InfinityPort" -> new InfinityInput(sys, centre);
            default             -> new SquareInput (sys, centre);
        };
    }

    private static OutputPort makeOutputPort(model.System sys,
                                             Point centre,
                                             String jsonName)
    {
        return switch (jsonName) {
            case "SquarePort"   -> new SquareOutput  (sys, centre);
            case "TrianglePort" -> new TriangleOutput(sys, centre);
            case "InfinityPort" -> new InfinityOutput(sys, centre);
            default             -> new SquareOutput  (sys, centre);
        };
    }
}
