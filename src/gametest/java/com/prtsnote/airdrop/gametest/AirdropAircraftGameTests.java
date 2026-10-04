package com.prtsnote.airdrop.gametest;

import com.prtsnote.airdrop.registry.ModEntities;
import com.prtsnote.airdrop.world.entity.AircraftAppearance;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.gametest.GameTestHolder;

import java.util.UUID;

@GameTestHolder("airdrop_supply_drops")
public final class AirdropAircraftGameTests {
    @GameTest(template = "airdrop_supply_drops:empty")
    public static void aircraftGeometryHasFiniteOutwardFaces(GameTestHelper helper) {
        int count = 0;
        for (var part : AircraftAppearance.BODY) {
            double cx = 0, cy = 0, cz = 0;
            for (var face : part.faces()) for (var point : new AircraftAppearance.Point[]{face.a(),face.b(),face.c(),face.d()}) {
                cx += point.x(); cy += point.y(); cz += point.z();
            }
            cx /= part.faces().size()*4; cy /= part.faces().size()*4; cz /= part.faces().size()*4;
            for (var face : part.faces()) {
                for (var point : new AircraftAppearance.Point[]{face.a(),face.b(),face.c(),face.d(),face.normal()}) {
                    helper.assertTrue(Float.isFinite(point.x()) && Float.isFinite(point.y()) && Float.isFinite(point.z()), "Geometry and normals must be finite");
                }
                var normal = face.normal();
                helper.assertTrue(Math.abs(normal.x()*normal.x()+normal.y()*normal.y()+normal.z()*normal.z()-1) < 0.00001,
                        "Every face must have a unit normal, including tapered panels");
                double fx = (face.a().x()+face.b().x()+face.c().x()+face.d().x())/4.0;
                double fy = (face.a().y()+face.b().y()+face.c().y()+face.d().y())/4.0;
                double fz = (face.a().z()+face.b().z()+face.c().z()+face.d().z())/4.0;
                helper.assertTrue((fx-cx)*normal.x()+(fy-cy)*normal.y()+(fz-cz)*normal.z() >= -0.001,
                        "Mirrored solid faces must point away from the part interior");
                if (Math.abs(face.a().x()) >= 7 && Math.abs(face.b().x()) >= 7 && face.normal().y() > 0.5) {
                    helper.assertTrue(face.a().y() >= 0.5 && face.b().y() >= 0.5, "Both wing tips must have outward upward-facing top surfaces");
                }
                count++;
            }
        }
        helper.assertTrue(count < 700, "Cached model geometry must remain bounded for distant events");
        var port = AircraftAppearance.LAMPS.get(0);
        var starboard = AircraftAppearance.LAMPS.get(1);
        helper.assertTrue(port.position().x() > 8 && starboard.position().x() < -8 && port.rgb() == 0xFF3024 && starboard.rgb() == 0x35FF78,
                "With +Z forward, port must be red and starboard green at the outer wing tips");
        helper.assertTrue(AircraftAppearance.FULL_BRIGHT == 15728880, "Light fixtures must use full block and sky brightness even at night");
        helper.succeed();
    }

    @GameTest(template = "airdrop_supply_drops:empty")
    public static void aircraftFlashTimingSurvivesResume(GameTestHelper helper) {
        var white = AircraftAppearance.LAMPS.get(2);
        helper.assertTrue(AircraftAppearance.intensity(white,0.5) == 1 && AircraftAppearance.intensity(white,2.5) == 0
                && AircraftAppearance.intensity(white,4.5) == 1 && AircraftAppearance.intensity(white,6.5) == 0,
                "Wing strobes must produce two short flashes separated by darkness");
        helper.assertTrue(AircraftAppearance.intensity(white,29.9) == 0 && AircraftAppearance.intensity(white,30.5) == 1,
                "White flashes must repeat every 1.5 seconds at 20 TPS");
        var upper = AircraftAppearance.LAMPS.get(5);
        var lower = AircraftAppearance.LAMPS.get(6);
        helper.assertTrue(AircraftAppearance.intensity(upper,2) > 0.99 && AircraftAppearance.intensity(lower,2) == 0
                && AircraftAppearance.intensity(upper,12) == 0 && AircraftAppearance.intensity(lower,12) > 0.99,
                "Upper and lower red beacons must alternate instead of lighting the whole aircraft together");
        var plane = ModEntities.PLANE.get().create(helper.getLevel());
        plane.configure(UUID.randomUUID(), helper.absolutePos(BlockPos.ZERO), 137, 184);
        var restored = ModEntities.PLANE.get().create(helper.getLevel());
        restored.load(plane.saveWithoutId(new CompoundTag()));
        for (var lamp : AircraftAppearance.LAMPS) {
            for (int frame = 0; frame < 16; frame++) {
                double age = plane.flightAge()+frame/16.0;
                double before = AircraftAppearance.intensity(lamp,age);
                double after = AircraftAppearance.intensity(lamp,restored.flightAge()+frame/16.0);
                helper.assertTrue(before == after && before >= 0 && before <= 1, "Fractional-frame flash phases must survive entity save/reload");
            }
        }
        helper.succeed();
    }
}
