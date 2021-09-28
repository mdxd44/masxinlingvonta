package net.superblaubeere27.masxinlingvonta;

import com.google.gson.Gson;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import net.superblaubeere27.masxinlingvaj.MLV;
import net.superblaubeere27.masxinlingvaj.compiler.MLVCompiler;
import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerClass;
import net.superblaubeere27.masxinlingvaj.compiler.tree.CompilerMethod;
import net.superblaubeere27.masxinlingvaj.preprocessor.AbstractPreprocessor;
import net.superblaubeere27.masxinlingvaj.preprocessor.AnnotationPreprocessor;
import net.superblaubeere27.masxinlingvaj.preprocessor.CompilerPreprocessor;
import org.apache.commons.cli.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;

import static org.bytedeco.llvm.global.LLVM.LLVMPrintModuleToString;

public class CLIMain {

    public static void main(String[] args) {
        // create Options object
        Options options = new Options();

        options.addRequiredOption("i", "inputJar", true, "input jar");
        options.addRequiredOption("o", "outputJar", true, "output file");

        options.addOption("ll", "irOutput", true, "ir output file");
        options.addOption("llvmDir", true, "llvm install location");
        options.addOption("outputDir", true, "a folder where the built shared libraries will be placed");
        options.addOption("compileFor", true, "select OSs to compile to");
        options.addOption("c", "config", true, "compiler config");
        options.addOption("help", "prints a help page");

        options.addOption("createNatives", "creates natives in outputDir");
        options.addOption("inJarNativesPath", true, "path to natives");

        DefaultParser parser = new DefaultParser();

        CommandLine parse;

        try {
            parse = parser.parse(options, args);
        } catch (ParseException e) {
            new HelpFormatter().printHelp("masxinlingvonta", options);

            return;
        }

        if (parse.hasOption("help")) {
            new HelpFormatter().printHelp("masxinlingvonta", options);

            return;
        }

        MLVCLIConfig config = null;

        var configFile = parse.getOptionValue("config");

        if (configFile != null) {
            try (FileInputStream fin = new FileInputStream(configFile)) {
                config = new Gson().fromJson(new InputStreamReader(fin), MLVCLIConfig.class);
            } catch (IOException e) {
                System.err.println("Failed to read config:");
                e.printStackTrace();
                return;
            }
        }

        MLVCLIConfig finalConfig = config;

        var mlv = new MLV(new CompilerPreprocessor(
                new AbstractPreprocessor() {
                    @Override
                    public void init(MLVCompiler compiler, CompilerPreprocessor preprocessor) throws Exception {
                        List<MLVCLIConfigPair> ignoredMethodsPatterns = new ArrayList<>();
                        if (finalConfig != null && finalConfig.ignoredMethods != null) {
                            for (MLVMethod mlvMethod : finalConfig.ignoredMethods) {
                                ignoredMethodsPatterns.add(new MLVCLIConfigPair(compileExcludePattern(mlvMethod.owner), compileExcludePattern(mlvMethod.name), compileExcludePattern(mlvMethod.desc)));
                            }
                        }

                        for (CompilerClass aClass : compiler.getIndex().getClasses()) {
                            for (CompilerMethod method : aClass.getMethods()) {
                                if ((method.getNode().access & (Opcodes.ACC_NATIVE | Opcodes.ACC_ABSTRACT)) != 0)
                                    continue;
                                if (method.getNode().name.startsWith("<"))
                                    continue;

                                List<Integer> methodOpcodes = Arrays.stream(method.getNode().instructions.toArray())
                                    .map(AbstractInsnNode::getOpcode)
                                    .collect(Collectors.toList());

                                //System.out.println(method.getNode().name + " " + methodOpcodes);
                                if (!methodOpcodes.contains(Opcodes.DUP2_X1) && !methodOpcodes.contains(Opcodes.DUP2_X2)) {
                                    if (!ignoredMethodsPatterns.isEmpty()) {
                                        if (!isMethodIgnored(ignoredMethodsPatterns, aClass, method)) {
                                            preprocessor.markForCompilation(method);
                                        } else {
                                            System.out.println("Method \""
                                                + method.getNode().name
                                                + "\" (Parent: \"" + aClass.getName()
                                                + "\", desc: \"" + method.getNode().desc
                                                + "\") will be ignored by config.");
                                        }
                                    } else {
                                        preprocessor.markForCompilation(method);
                                    }
                                } else {
                                    System.out.println("Unsupported opcode! Method \""
                                        + method.getNode().name
                                        + "\" (Parent: \"" + aClass.getName()
                                        + "\", desc: \"" + method.getNode().desc
                                        + "\") will be ignored.");
                                }
                            }
                        }
                    }

                    @Override
                    public void preprocess(CompilerMethod method, CompilerPreprocessor preprocessor) throws Exception {

                    }
                }
                , new AnnotationPreprocessor()
        ));

        String inJarPath = parse.getOptionValue("inJarNativesPath");

        try {
            System.out.println("Loading input...");
            mlv.loadInput(new File(parse.getOptionValue("inputJar")));

            System.out.println("Compiling...");
            mlv.preprocessAndCompile(inJarPath);

            System.out.println("Writing...");
            mlv.writeOutput(new File(parse.getOptionValue("outputJar")));

            System.out.println("Optimizing IR...");
            mlv.optimize(1/*3*/);
        } catch (Exception e) {
            System.err.println("Exception while compiling: ");

            e.printStackTrace();
            return;
        }

        String llvmDir = parse.getOptionValue("llvmDir");
        boolean createNatives = parse.hasOption("createNatives");
        String outputDir = !createNatives ? System.getProperty("java.io.tmpdir") : parse.getOptionValue("outputDir");

        try {
            File tmpIROutput = File.createTempFile("llvmir", ".ll");

            try {
                var ir = LLVMPrintModuleToString(mlv.getLLVMModule()).getStringBytes();

                Files.write(tmpIROutput.toPath(), ir);

                String irOutput = parse.getOptionValue("irOutput");

                if (irOutput != null)
                    Files.write(Paths.get(irOutput), ir);

                if (parse.getOptionValue("compileFor") != null) {
                    var oss = Arrays.stream(parse.getOptionValue("compileFor").split(",")).map(OS::fromString).collect(
                            Collectors.toSet());

                    Map<String, String> env = new HashMap<>();
                    env.put("create", "true");

                    URI uri = URI.create("jar:" + Paths.get(parse.getOptionValue("outputJar")).toUri());

                    for (OS os : oss) {
                        if (os == OS.MAC)
                            throw new IllegalStateException("Mac OS is not supported yet");

                        compileFor(tmpIROutput, llvmDir, outputDir, os);

                        try (FileSystem fs = FileSystems.newFileSystem(uri, env)) {
                            String fileName;
                            switch (os) {
                                case WINDOWS:
                                    fileName = "win64.dll";
                                    break;
                                case LINUX:
                                    fileName = "linux64.so";
                                    break;
                                default:
                                    throw new IllegalStateException("Unexpected value: " + os);
                            }
                            Path pathToLib = Paths.get(getFilePath(outputDir, fileName));
                            Path nf = fs.getPath((inJarPath == null ? "META-INF/natives/" : inJarPath) + fileName);

                            Files.createDirectories(nf.getParent());
                            Files.copy(pathToLib, nf, StandardCopyOption.REPLACE_EXISTING);

                            if (!createNatives) {
                                pathToLib.toFile().delete();
                            }
                        }
                    }

                }
            } finally {
                tmpIROutput.delete();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("Compilation finished.");
    }

    private static void compileFor(File irInput, String llvmBasePath, String outputDir, OS os) throws IOException {
        File tmpObjFile = File.createTempFile("tmp_obj", ".o");

        System.out.println("Compiling for " + os + "...");

        try {
            var link = true;

            {
                Process compilerProcess;

                if (os == OS.WINDOWS) {
                    compilerProcess = new ProcessBuilder(getFilePath(llvmBasePath, "clang"),
                                                         "-O3",
                                                         "-shared",
                                                         "-target",
                                                         os.getTargetTriple(),
                                                         "-o",
                                                         getFilePath(outputDir, "win64.dll"),
                                                         irInput.getAbsolutePath()
                    ).start();

                    link = false;
                } else if (os == OS.LINUX || os == OS.MAC) {
                    compilerProcess = new ProcessBuilder(getFilePath(llvmBasePath, "clang"),
                                                         "-O3",
                                                         "-c",
                                                         "-target",
                                                         os.getTargetTriple(),
                                                         "-fPIC",
                                                         "-o",
                                                         tmpObjFile.getAbsolutePath(),
                                                         irInput.getAbsolutePath()
                    ).start();
                } else {
                    throw new IllegalArgumentException();
                }

                var exitCode = awaitProcess(compilerProcess);

                if (exitCode != 0)
                    throw new IOException("Compiler returned a non-zero exit code: " + exitCode);
            }

            if (!link)
                return;

            Process linkerProcess;

            if (os == OS.LINUX) {
                linkerProcess = new ProcessBuilder(getFilePath(llvmBasePath, "ld.lld"),
                                                   "-shared",
                                                   "-o",
                                                   getFilePath(outputDir, "linux64.so"),
                                                   tmpObjFile.getAbsolutePath()
                ).start();
            } else if (os == OS.MAC) {
                linkerProcess = new ProcessBuilder(getFilePath(llvmBasePath, "ld64.lld"),
                                                   "-dylib",
                                                   "-o",
                                                   getFilePath(outputDir, "macosx.dynlib"),
                                                   tmpObjFile.getAbsolutePath()
                ).start();
            } else {
                throw new IllegalArgumentException();
            }

            var exitCode = awaitProcess(linkerProcess);

            if (exitCode != 0)
                throw new IOException("Linker returned a non-zero exit code: " + exitCode);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            tmpObjFile.delete();
        }


    }

    private static int awaitProcess(Process process) throws InterruptedException, IOException {
        process.waitFor();

        process.getInputStream().transferTo(System.err);
        process.getErrorStream().transferTo(System.err);

        return process.exitValue();
    }

    private static String getFilePath(String basePath, String fileName) {
        if (basePath == null)
            return fileName;

        return new File(basePath, fileName).getAbsolutePath();
    }

    private static Pattern compileExcludePattern(String s) {
        StringBuilder sb = new StringBuilder();

        char[] chars = s.toCharArray();

        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];

            if (c == '*') {
                if (chars.length - 1 != i && chars[i + 1] == '*') {
                    sb.append(".*");
                    i++;
                } else {
                    sb.append("[^/]*");
                }
            } else if (c == '.') {
                sb.append('/');
            } else {
                sb.append(c);
            }
        }

        return Pattern.compile(sb.toString());
    }

    private static boolean isMethodIgnored(List<MLVCLIConfigPair> patterns, CompilerClass aClass, CompilerMethod method) {
        for (MLVCLIConfigPair ignoredMethodsPattern : patterns) {
            return ignoredMethodsPattern.ownerPattern.matcher(aClass.getName()).matches()
                && ignoredMethodsPattern.namePattern.matcher(method.getNode().name).matches()
                && ignoredMethodsPattern.descPattern.matcher(method.getNode().desc).matches();
        }

        return false;
    }

    @SuppressWarnings("unused")
    private static class MLVCLIConfig {
        private MLVMethod[] ignoredMethods;
    }

    @SuppressWarnings("unused")
    private static class MLVMethod {
        private String owner;
        private String name;
        private String desc;
    }

    private static class MLVCLIConfigPair {
        private final Pattern ownerPattern;
        private final Pattern namePattern;
        private final Pattern descPattern;

        private MLVCLIConfigPair(Pattern ownerPattern, Pattern namePattern, Pattern descPattern) {
            this.ownerPattern = ownerPattern;
            this.namePattern = namePattern;
            this.descPattern = descPattern;
        }
    }

    enum OS {
        WINDOWS,
        LINUX,
        MAC;

        static OS fromString(String name) {
            switch (name.toLowerCase(Locale.ROOT)) {
                case "windows":
                    return WINDOWS;
                case "linux":
                    return LINUX;
                case "mac":
                    return MAC;
            }

            throw new IllegalArgumentException("Invalid OS name: " + name + ". Supported OSes: " + Arrays.toString(
                    values()));
        }

        String getTargetTriple() {
            switch (this) {
                case WINDOWS:
                    return "x86_64-pc-windows-gnu";
                case LINUX:
                    return "x86_64-pc-linux-gnu";
                case MAC:
                    return "x86_64-apple-darwin";
            }

            throw new IllegalStateException("Unexpected value: " + this);
        }
    }

}
