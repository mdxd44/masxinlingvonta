# masxinlingvonta

Compiles Java ByteCode to LLVM IR and native code (for obfuscation purposes). Contributions are welcome!

```java
    static {
        boolean is64 = System.getProperty("os.arch").contains("64");
        String osName = System.getProperty("os.name").toLowerCase();
        String libPath = null;
        if (is64) {
            if (osName.contains("win")) {
                libPath = "/META-INF/natives/win64.dll"; // or can be put anywhere
            } else if (osName.contains("linux")) {
                libPath = "/META-INF/natives/linux64.so"; // or can be put anywhere
            }
        }
        if (libPath == null) {
            throw new RuntimeException("No natives found for " + osName + (is64 ? " x86-64" : " x86"));
        }

        File libFile;
        try {
            libFile = File.createTempFile("lib", null);
            libFile.deleteOnExit();
            if (!libFile.exists()) {
                throw new IOException("A temp file was expected, but it wasn't created");
            }
        } catch (IOException e) {
            throw new UnsatisfiedLinkError("Failed to create temp file");
        }

        try (InputStream is = YourMainClass.class.getResourceAsStream(libPath);
            FileOutputStream fos = new FileOutputStream(libFile)) {
            byte[] buf = new byte[2048];
            int read;
            while ((read = is.read(buf)) != -1) {
                fos.write(buf, 0, read);
            }
        } catch (IOException e) {
            throw new UnsatisfiedLinkError("Failed to copy file: " + e.getMessage());
        }

        System.load(libFile.getAbsolutePath());
    }
```

## Prerequisites

- [LLVM](https://releases.llvm.org/download.html) (For compiling the generated LLVM-IR to native code)
- [Maven](https://maven.apache.org/) (For building this project)
- Java 11 ([Download](https://adoptopenjdk.net/releases.html?variant=openjdk11))
- (For Windows) ~~Windows SDK / Visual Studio with Visual C++ support~~ MSYS2 MinGW64 and `C:\msys64\mingw64\bin` in `PATH` variable

## Building from source

You can simply build this project with one simple command:

`mvn -Djavacpp.platform=<HOST-PLATFORM> clean package`

The `HOST-PLATFORM` is the platform you want to run this tool on, **NOT** the platform the compile code should run on.
Supported host targets: `windows-x86_64`, `linux-x86_64`, `macosx-x86_64`

## Features

This project is not production-safe. Please test if the code compiles correctly before publishing!

If you found bugs or missing features, feel free to open an issue.

### Supported

- Automatic generation of Linux and Windows binaries
- Windows Host platform
- Most of the latest JVM's instruction set
- Exception Handling
- Kotlin

### Partially supported

- Building Windows binaries on non Windows hosts (Requires Windows STLs)
- Mac OSX (Requires you to build the shared libraries yourself)
- Floating Point operations (Edge cases like division by zero might cause behaviour that does not match the JVM
  specification)
- Lambdas (since they are using invokedynamic)
- `INVOKEDYNAMIC` instruction (methods using this instruction will be compiled, but the `INVOKEDYNAMIC` instruction is
  extracted to a new method)
- `MULTIANEWARRAY` instruction (will be extracted to a new method)

### Not supported

- `DUP2_X1` and `DUP2_X2` instructions which are generated by get-and-... operations on double- and
  long-arrays (`doubleArray[0]++`, `doubleArray[0] += 2.0`, `longArray[0]++`)

### Planned features

- Native code obfuscation (String Encryption, Constant obfuscation, Stack Strings)
- Hiding exported methods via `JNI_OnLoad` and `RegisterNatives`
- Automatic inlining of short methods
- Integrated obfuscation

## Usage

### Preparation

Before compiling, you have to add code to your program that loads the generated native libraries.
[Here](https://github.com/superblaubeere27/masxinlingvonta/blob/efa820dd7a6a0a188ea5e83403caaa1ede0bd182/masxinlingvonta-core/src/main/java/net/superblaubeere27/masxinlingvaj/postprocessor/extensions/NativeLoaderExtension.java)
is an example for a native loader that assumes that the natives are stored in `META-INF/natives`. To use it, just
add `NativeLoader.loadNatives()` to the main-method of your program.

Applying NameObfuscation via [ProGuard](https://www.guardsquare.com/proguard) and another bytecode obfuscator for
further obfuscation (e.g. [ZKM](http://www.zelix.com/) (commercial)
, [obfuscator](https://github.com/superblaubeere27/obfuscator), [Radon](https://github.com/ItzSomebody/Radon), etc.) is
recommended.

### Target selection

Before you think about target selection, you should first read about the limitations of this tool and the method it
uses (see below).

By default, this tool does not compile every method in your program. You have to specify the methods you want to compile
manually using either the annotation api or the config.

### Compiling via CLI

If you want to compile shared libraries for your target OSes:

```
java -jar masxinlingvonta-cli.jar -i input.jar -o obfuscated.jar -compileFor windows,linux -llvmDir "C:\Program Files\LLVM\bin"
```

If you want to compile the generated IR yourself:

```
java -jar masxinlingvonta-cli.jar -i input.jar -o obfuscated.jar -ll output.ll
```

CLI Options:

|Optional |Option Name |Short alias  | Description|
--- | --- | --- | ---
No|`--inputJar <FILE>`|`-i`|Input JAR|
No|`--outputJar <FILE>`|`-i`|Output JAR|
Yes|`--config <FILE>`|`-c`|Allows you to specify a config (see below)|
Yes|`--irOutput <FILE>`|`-ll`|Dumps the generated LLVM IR|
Yes|`-compileFor <OS1,OS2,...>`| |Compiles shared libraries for the specified targets|
Yes|`-outputDir`| |Selects a directory where the natives generated by `-compileFor` will be placed, ignored without `-createNatives`|
Yes|`-createNatives`| |Will natives files be created in `-outputDir`?|
Yes|`-inJarNativesPath`| |Path to natives in jar file, by default `META-INF/natives`|
Yes|`-llvmDir`| |LLVM's `bin` folder|
Yes|`-help`| |Prints a help page|

## Configuration

Here is an example configuration (should be self-explanatory)

```JSON
{
  "ignoredMethods": [
    {
      "owner": "net.superblaubeere27.Main",
      "name": "superHeavyMethod",
      "desc": "(Ljava/lang/String;)Z"
    },
    {
      "owner": "ru.mdxd44.main.*",
      "name": "*",
      "desc": "**String**"
    },
    {
      "owner": "ru.mdxd44.mixins.**",
      "name": "func*",
      "desc": "*"
    }
  ]
}
```

## Annotations

To use annotations, include the `masxinlingvonta-annotations` JAR in your class path.

For now there is only one annotation:

| Annotation | Target | Description |
--- | --- | ---
`@Outsource` | Methods | Methods with this annotation will be compiled to native code |

## Limitations

### Garbage Collection

Local references in a method are only freed when the method returns. For example function with loops like this could
cause memory leaks:

```Java
@Outsource
private void foo(SocketServer server)throws Exception{
    while (true) {
        Socket sock = server.accept();
        // ...
    
        // The JVM would free the reference to sock which allows it to be garbage collected
    }
} // masxinlingvonta would free the references on return. 
```

To prevent a memory leak, you should consider extracting the inner part of loops that your program will be stuck in for
a while:

```Java
@Outsource
private void foo(SocketServer server)throws Exception{
    while (true) {
        bar(server);
    }
}

@Outsource
private void foo(SocketServer server)throws Exception{
    Socket sock = server.accept();
    // ...
    // The local reference to sock will be freed here
  }
```

### Performance

Every JVM-interop is handled via JNI-methods. Only primitive operations like arithmetics, branches etc. are independent.

This dramatically slows down the code, so only a few select methods should be compiled to native code
