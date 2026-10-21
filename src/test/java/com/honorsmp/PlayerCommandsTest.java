package com.honorsmp;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.command.permission.LeveledPermissionPredicate;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Registers the REAL command tree (Cmds.register) and checks every player command from a permission
 * level 0 source whose entity is a (memory-only) ServerPlayerEntity.
 *
 * Covers: requirement predicates on every node, full parse (no syntax error, input fully consumed,
 * a command is attached), and that execution gets past the permission layer into our handler.
 * Does NOT cover: the handler bodies (no MinecraftServer / world / Honor instance exists in a unit
 * test, so execution stops at "HonorSMP is not ready yet."), nor opening real chest screens.
 */
class PlayerCommandsTest {
    static final List<String> PLAYER_COMMANDS = List.of(
            "honor", "honor top", "honor tracker",
            "donate", "donate food", "donate food and more words", "donation", "donation claim",
            "appeal I was framed", "appeal 3 I was framed",
            "fine", "fine pay", "fine pay 3",
            "bounty set Steve", "bounty claim", "bounty list", "bounty list text",
            "civ create My Civ", "civ invite Steve", "civ join My Civ", "civ leave", "civ kick Steve",
            "civ promote Steve", "civ guard Steve", "civ disband",
            "civ info", "civ info My Civ", "civ list", "civ top", "civ chest",
            "cc", "cc hello civ");

    /** Commands typed without their argument: allowed, and answer with a Usage line. */
    static final List<String> BARE = List.of("appeal", "bounty set", "civ create", "civ invite", "civ join",
            "civ kick", "civ promote", "civ guard");

    CommandDispatcher<ServerCommandSource> d;
    ServerCommandSource src;

    static boolean booted;

    static synchronized void bootstrap() {
        if (booted) return;
        booted = true;
        net.minecraft.SharedConstants.createGameVersion();
        try {
            net.minecraft.Bootstrap.initialize();
        } catch (IllegalAccessError ignored) {
            // access wideners are not applied in plain unit tests; registries are usable anyway
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        bootstrap();
        Honor.I = null;
        d = new CommandDispatcher<>();
        Cmds.register(d);
        // A ServerPlayerEntity without a server: allocate without running constructors.
        var uf = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        uf.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) uf.get(null);
        ServerPlayerEntity fake = (ServerPlayerEntity) unsafe.allocateInstance(ServerPlayerEntity.class);
        src = new ServerCommandSource(
                net.minecraft.server.command.CommandOutput.DUMMY, Vec3d.ZERO, Vec2f.ZERO, null,
                LeveledPermissionPredicate.ALL, "Steve", Text.literal("Steve"), null, (Entity) fake);
    }

    @Test
    void sourceIsLevelZero() {
        assertFalse(src.getPermissions().hasPermission(net.minecraft.command.DefaultPermissions.MODERATORS));
        assertFalse(src.getPermissions().hasPermission(net.minecraft.command.DefaultPermissions.GAMEMASTERS));
        assertNotNull(src.getPlayer());
    }

    @Test
    void bareCommandsGiveUsageNotAnonymousError() {
        for (String cmd : BARE) {
            var e = assertThrows(CommandSyntaxException.class, () -> d.execute(cmd, src), cmd);
            assertTrue(e.getRawMessage().getString().startsWith("Usage: /"), cmd + " -> " + e.getRawMessage().getString());
        }
    }

    @Test
    void everyPlayerCommandIsAllowedAndParses() {
        for (String cmd : PLAYER_COMMANDS) {
            ParseResults<ServerCommandSource> pr = d.parse(cmd, src);
            assertTrue(pr.getExceptions().isEmpty(), cmd + " parse errors: " + pr.getExceptions());
            assertFalse(pr.getReader().canRead(), cmd + " was not fully understood, left: '" + pr.getReader().getRemaining() + "'");
            assertNotNull(pr.getContext().getCommand(), cmd + " has no executable command at that point");
            for (var n : pr.getContext().getNodes()) {
                assertTrue(n.getNode().canUse(src), cmd + ": node '" + n.getNode().getName() + "' needs more than level 0");
            }
        }
    }

    @Test
    void everyRootNodeIsUsableAtLevelZero() {
        for (String root : List.of("honor", "civ", "cc", "bounty", "appeal", "fine", "donate", "donation")) {
            CommandNode<ServerCommandSource> n = d.getRoot().getChild(root);
            assertNotNull(n, root + " is not registered");
            assertTrue(n.canUse(src), "/" + root + " is hidden from non-ops");
        }
    }

    @Test
    void executionReachesOurHandlerNotAPermissionError() {
        for (String cmd : PLAYER_COMMANDS) {
            try {
                d.execute(cmd, src);
                fail(cmd + " should not succeed with no Honor instance");
            } catch (CommandSyntaxException e) {
                String m = e.getRawMessage().getString();
                assertTrue(m.startsWith("HonorSMP is not ready yet."),  cmd + " failed before reaching its handler: " + m);
            }
        }
    }

    @Test
    void donateWithExtraWordsParsesLikeDonate() {
        var a = d.parse("donate", src);
        var b = d.parse("donate food", src);
        assertNotNull(a.getContext().getCommand());
        assertSame(a.getContext().getCommand(), b.getContext().getCommand());
    }
}
