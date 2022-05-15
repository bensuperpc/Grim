package ac.grim.grimac.checks.impl.scaffolding;

import ac.grim.grimac.checks.CheckData;
import ac.grim.grimac.checks.type.BlockPlaceCheck;
import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.utils.anticheat.update.BlockPlace;
import ac.grim.grimac.utils.collisions.datatypes.SimpleCollisionBox;
import ac.grim.grimac.utils.data.Pair;
import ac.grim.grimac.utils.nmsutil.Ray;
import ac.grim.grimac.utils.nmsutil.ReachUtils;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.world.BlockFace;
import com.github.retrooper.packetevents.protocol.world.states.type.StateTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@CheckData(name = "RotationPlace")
public class RotationPlace extends BlockPlaceCheck {
    double flagBuffer = 0; // If the player flags once, force them to play legit, or we will cancel the tick before.

    public RotationPlace(GrimPlayer player) {
        super(player);
    }

    @Override
    public void onBlockPlace(final BlockPlace place) {
        if (place.getMaterial() == StateTypes.SCAFFOLDING) return;
        boolean hit = didRayTraceHit(place);
        if (!hit && flagBuffer > 0) {
            // If the player hit and has flagged this check recently
            place.resync(); // Deny the block placement.
            flagAndAlert("pre-flying");
        }
    }

    // Use post flying because it has the correct rotation, and can't false easily.
    @Override
    public void onPostFlyingBlockPlace(BlockPlace place) {
        if (place.getMaterial() == StateTypes.SCAFFOLDING) return;
        // Ray trace to try and hit the target block.
        boolean hit = didRayTraceHit(place);
        // This can false with rapidly moving yaw in 1.8+ clients
        if (!hit) {
            flagBuffer = 1;
            flagAndAlert("post-flying");
        } else {
            flagBuffer = Math.max(0, flagBuffer - 0.1);
        }
    }

    private boolean didRayTraceHit(BlockPlace place) {
        SimpleCollisionBox box = new SimpleCollisionBox(place.getPlacedBlockPos());

        List<Vector3f> possibleLookDirs = new ArrayList<>(Arrays.asList(
                new Vector3f(player.lastXRot, player.yRot, 0),
                new Vector3f(player.xRot, player.yRot, 0)
        ));

        // 1.9+ players could be a tick behind because we don't get skipped ticks
        if (player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_9)) {
            possibleLookDirs.add(new Vector3f(player.lastXRot, player.lastYRot, 0));
        }

        // 1.7 players do not have any of these issues! They are always on the latest look vector
        if (player.getClientVersion().isOlderThan(ClientVersion.V_1_8)) {
            possibleLookDirs = Collections.singletonList(new Vector3f(player.xRot, player.yRot, 0));
        }

        for (double d : player.getPossibleEyeHeights()) {
            for (Vector3f lookDir : possibleLookDirs) {
                // x, y, z are correct for the block placement even after post tick because of code elsewhere
                Vector3d starting = new Vector3d(player.x, player.y + d, player.z);
                // xRot and yRot are a tick behind
                Ray trace = new Ray(player, starting.getX(), starting.getY(), starting.getZ(), lookDir.getX(), lookDir.getY());
                Pair<Vector, BlockFace> intercept = ReachUtils.calculateIntercept(box, trace.getOrigin(), trace.getPointAtDistance(6));

                if (intercept.getFirst() != null) return true;
            }
        }

        return false;
    }
}
