package net.superblaubeere27.masxinlingvaj;

import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerMethod;
import net.superblaubeere27.masxinlingvaj.io.InputLoader;
import net.superblaubeere27.masxinlingvaj.io.OutputWriter;
import net.superblaubeere27.masxinlingvaj.postprocessor.CompilerPostprocessor;
import net.superblaubeere27.masxinlingvaj.preprocessor.CompilerPreprocessor;
import net.superblaubeere27.masxinlingvaj.utils.ExecutorServiceFactory;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.llvm.LLVM.LLVMModuleRef;
import org.bytedeco.llvm.LLVM.LLVMPassManagerBuilderRef;
import org.bytedeco.llvm.LLVM.LLVMPassManagerRef;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.Executors;

import static org.bytedeco.llvm.global.LLVM.*;

public class MLV {
    private static final ExecutorServiceFactory EXECUTOR_SERVICE_FACTORY = () -> Executors.newFixedThreadPool(12);
    public static String inJarNativesPath;

    private final CompilerPreprocessor preprocessor;
    private InputLoader.ReadInput input;
    private MLVCompiler compiler;

    public MLV(CompilerPreprocessor preprocessor) {
        this.preprocessor = preprocessor;
    }

    public void loadInput(File jar) throws IOException {
        this.input = InputLoader.loadFiles(Collections.singletonList(jar.toURI().toURL()), EXECUTOR_SERVICE_FACTORY);
    }

    public void preprocessAndCompile(String inJarNativesPath1) throws Exception {
        inJarNativesPath = inJarNativesPath1;
        this.compiler = new MLVCompiler(this.input.getClassNodes());

        preprocessor.preprocess(compiler);

        for (CompilerMethod compilerMethod : preprocessor.getMethodsToCompile()) {
            compiler.compileMethod(compilerMethod);
        }

        new CompilerPostprocessor().postprocess(this.compiler);
    }

    public void writeOutput(File file) throws IOException {
        var encoded = OutputWriter.encodeChangedClasses(this.compiler, this.input.getRawData());

        try (FileOutputStream fos = new FileOutputStream(file)) {
            OutputWriter.writeZipFile(fos, encoded);
        }
    }

    public void optimize(int lvl) {
        var module = compiler.getModule();

        BytePointer error = new BytePointer((Pointer) null);

        LLVMPassManagerRef pass = LLVMCreatePassManager();
        LLVMPassManagerBuilderRef passManagerBuilder = LLVMPassManagerBuilderCreate();

        LLVMPassManagerBuilderSetOptLevel(passManagerBuilder, lvl);

        LLVMPassManagerBuilderPopulateModulePassManager(passManagerBuilder, pass);

        LLVMVerifyModule(module, LLVMAbortProcessAction, error);
        LLVMDisposeMessage(error); // Handler == LLVMAbortProcessAction -> No need to check errors

        LLVMRunPassManager(pass, module);

        LLVMDisposePassManager(pass);
    }

    public LLVMModuleRef getLLVMModule() {
        return this.compiler.getModule();
    }
}
