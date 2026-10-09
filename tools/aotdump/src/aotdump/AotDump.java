package aotdump;

import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

// A build tool, not a mod to play with: with -Daotdump.out=DIR, at pre-launch it asks Fabric's class loader (Knot) for
// the final bytes of every class of the game and the mods - after Fabric's own transforms and every mixin - writes them
// under DIR and exits. The native image is built from those, since it cannot transform classes at run time.
public class AotDump implements PreLaunchEntrypoint {
    @Override public void onPreLaunch() {
        String out = System.getProperty("aotdump.out");
        if (out == null) return;
        try {
            ClassLoader knot = AotDump.class.getClassLoader();
            Method getDelegate = knot.getClass().getDeclaredMethod("getDelegate");
            getDelegate.setAccessible(true);
            Object delegate = getDelegate.invoke(knot);
            Method post = delegate.getClass().getDeclaredMethod("getPostMixinClassByteArray", String.class, boolean.class);
            post.setAccessible(true);
            Path dir = Paths.get(out);
            int done = 0, missing = 0, failed = 0;
            List<String> errors = new ArrayList<>();
            for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
                for (Path root : mod.getRootPaths()) {
                    List<String> names = new ArrayList<>();
                    try (Stream<Path> s = Files.walk(root)) {
                        s.filter(p -> p.toString().endsWith(".class")).forEach(p -> {
                            String rel = root.relativize(p).toString().replace('\\', '/');
                            if (!rel.startsWith("META-INF/") && !rel.equals("module-info.class"))
                                names.add(rel.substring(0, rel.length() - 6).replace('/', '.'));
                        });
                    }
                    for (String name : names) {
                        try {
                            byte[] b = (byte[]) post.invoke(delegate, name, false);
                            if (b == null) { missing++; continue; }
                            Path f = dir.resolve(name.replace('.', '/') + ".class");
                            Files.createDirectories(f.getParent());
                            Files.write(f, b);
                            done++;
                        } catch (Throwable t) {
                            failed++;
                            Throwable c = t.getCause() != null ? t.getCause() : t;
                            if (errors.size() < 40) errors.add(name + ": " + c);
                        }
                    }
                }
            }
            System.out.println("aotdump: " + done + " classes written, " + missing + " not found, " + failed + " failed");
            for (String e : errors) System.out.println("aotdump:   " + e);
        } catch (Throwable t) {
            t.printStackTrace();
        }
        System.exit(0);
    }
}
