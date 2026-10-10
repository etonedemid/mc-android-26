import java.lang.classfile.*;
import java.nio.file.*;

// java ReturnFalsePatch.java CLASSFILE METHOD - make the static boolean no-argument METHOD of the class return false.
// Used on netty's CleanerJava25.isSupported: under native image netty assumes java.lang.foreign works on Java 25, and the
// Android image has no FFM support, so every direct buffer allocation failed. False sends netty to its older cleaner.
public class ReturnFalsePatch {
    public static void main(String[] a) throws Exception {
        Path p = Path.of(a[0]);
        int[] n = {0};
        ClassFile cf = ClassFile.of();
        byte[] out = cf.transformClass(cf.parse(p), ClassTransform.transformingMethods(
            m -> m.methodName().equalsString(a[1]) && m.methodType().equalsString("()Z"),
            MethodTransform.transformingCode(new CodeTransform() {
                boolean done;
                public void accept(CodeBuilder cb, CodeElement e) {
                    if (!done) { done = true; n[0]++; cb.iconst_0().ireturn(); }
                }
            })));
        if (n[0] != 1) { System.err.println(p + ": " + a[1] + "()Z not found"); System.exit(1); }
        Files.write(p, out);
        System.out.println(p + ": " + a[1] + " returns false");
    }
}
