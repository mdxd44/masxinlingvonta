package net.superblaubeere27.masxinlingvaj.compiler.tree;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;
import java.util.stream.Collectors;

/**
 * An indexed class
 */
public class CompilerClass {
    private final ClassNode classNode;
    private final List<CompilerMethod> methods;
    private final List<CompilerField> fields;
    private final boolean isLibrary;
    private boolean modifiedFlag;

    public CompilerClass(ClassNode classNode, boolean isLibrary) {
        this(classNode, isLibrary, false);
    }

    public CompilerClass(ClassNode classNode, boolean isLibrary, boolean wasSynthesized) {
        this.classNode = classNode;
        this.isLibrary = isLibrary;
        this.modifiedFlag = wasSynthesized;

        this.methods = classNode.methods.stream().map(x -> new CompilerMethod(this, x)).collect(Collectors.toList());
        this.fields = classNode.fields.stream().map(x -> new CompilerField(this, x)).collect(Collectors.toList());
    }

    public List<CompilerField> getFields() {
        return fields;
    }

    public List<CompilerMethod> getMethods() {
        return methods;
    }

    public ClassNode getClassNode() {
        return classNode;
    }

    public String getName() {
        return this.classNode.name;
    }

    public String suggestStaticMethodName(String methodDesc) {
        var ref = new Object() {
            String currentName;
        };

        int i = 0;
        StringBuilder xd = new StringBuilder();
        for (int i1 = 0; i1 < 40; i1++) {
           xd.append("̳̿\u202E̳̳̳̳̳̳̳̳̿̿̿̿̊̿̿̿̿̊ด้็็็็็้็็็็็้็็็็็้็็็็็้็็็็็้็็็็็้็็็็็ฏ๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎ํํํํํํํํํํํํํํํํํํํํํํํํํํ ̳̳̳̳̳̳̳̳̳̳̳̳̳̳̳̳̳̳̳̿̿̿̿̊̿̿̿̿̊̿̿̿̿̊̿̿̿̿̊̿̿̿ด้็็็็็้็็็็็้็็็็็้็็็็็้็็็็็้็็็็็้็็็็็ฏ๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎๎ํํํํํํํํํํํํํํํํํํํํํํํํํํ ̳̳̳̳̳̳̳̳̳̳̳̳̳̳̳̳̳̳̳̳̳̳̳̳̳̳̳̳̳̳̳̳̳̳̳̳̳̳̳̳̳̳̳̳̳̳̳̳̳̳̳̿̊̿̿̿̿̊̿̿̿̿̊̿̿̿̿̊̿̿̿̿̊̿̿̿̿̊̿̿̿̿̊̿̿̿̊̿̿̿̿̊̿̿̿̿̊̿̿̿̿̊̿̿̿̿̊̿̿̿̿̊̿|̳̳̳̳̿̿̿̿l̳̳̳̳̳̳̳̳̳̳̳̳̳̳̿̿̿̿̊̿̿̿̿̊̿̿̿̿̊̿");
        }

        do {
            ref.currentName = xd.toString() + (i++);
        } while (methods.stream().anyMatch(x -> x.getNode().desc.equals(methodDesc) && x.getNode().name.equals(ref.currentName)));

        return ref.currentName;
    }

    /**
     * Adds a method to this and the presented class
     */
    public void addMethod(MLVCompiler compiler, MethodNode extractedMethod) {
        this.classNode.methods.add(extractedMethod);

        this.methods.add(new CompilerMethod(this, extractedMethod));

        compiler.getIndex().refreshClass(this);
    }

    public void setModifiedFlag() {
        this.modifiedFlag = true;
    }

    public boolean getModifiedFlag() {
        return this.modifiedFlag;
    }
}
