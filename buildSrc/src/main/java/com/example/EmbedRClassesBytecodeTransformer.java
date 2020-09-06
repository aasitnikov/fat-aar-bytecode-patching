package com.example;

import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;
import javassist.bytecode.ClassFile;
import javassist.bytecode.ConstPool;
import kotlin.io.FilesKt;

import static com.android.build.api.transform.QualifiedContent.DefaultContentType.CLASSES;
import static com.android.build.api.transform.QualifiedContent.Scope.PROJECT;

/**
 * Transforms bytecode of classes, that are embedded with fat-aar.
 *
 * Because we embed multiple modules into one fat-aar, aapt wont generate
 * R class for embedded modules. So we just replace references of their R
 * classes with current module R class.
 */
public class EmbedRClassesBytecodeTransformer extends Transform {

    private final Map<String, String> classTransformTable;

    public EmbedRClassesBytecodeTransformer(final List<String> libraryPackages, final String targetPackage) {
        // We need jvm internal representation to use ConstPool.renameClass
        // Example transform: com.example.lib -> com/example/lib/R$style
        List<String> resourceTypes = Arrays.asList("anim", "animator", "array", "attr", "bool", "color", "dimen",
                "drawable", "font", "fraction", "id", "integer", "interpolator", "layout", "menu", "mipmap", "plurals",
                "raw", "string", "style", "styleable", "transition", "xml");

        HashMap<String, String> map = new HashMap<>();
        for (String resource : resourceTypes) {
            String targetClass = targetPackage.replace(".", "/") + "/R$" + resource;
            for (String libraryPackage : libraryPackages) {
                String fromClass = libraryPackage.replace(".", "/") + "/R$" + resource;
                map.put(fromClass, targetClass);
            }
        }
        classTransformTable = map;
    }

    @Override
    public String getName() {
        return "fatAarRTransform";
    }

    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        HashSet<QualifiedContent.ContentType> set = new HashSet<>();
        set.add(CLASSES);
        return set;
    }

    @Override
    public Set<? super QualifiedContent.Scope> getScopes() {
        HashSet<QualifiedContent.Scope> set = new HashSet<>();
        set.add(PROJECT);
        return set;
    }

    @Override
    public boolean isIncremental() {
        return true;
    }

    @Override
    public void transform(TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {
        final TransformOutputProvider outputProvider = transformInvocation.getOutputProvider();
        final File outputDir = outputProvider.getContentLocation("classes", getOutputTypes(), getScopes(), Format.DIRECTORY);

        try {
            for (final TransformInput input : transformInvocation.getInputs()) {
                for (final DirectoryInput directoryInput : input.getDirectoryInputs()) {
                    final File directoryFile = directoryInput.getFile();

                    final ClassPool classPool = new ClassPool();
                    classPool.insertClassPath(directoryFile.getAbsolutePath());

                    for (final File originalClassFile : getChangedClassesList(directoryInput)) {
                        if (!originalClassFile.getPath().endsWith(".class")) {
                            continue; // ignore anything that is not class file
                        }
                        File relative = FilesKt.relativeTo(originalClassFile, directoryFile);
                        String className = filePathToClassname(relative);
                        final CtClass ctClass = classPool.get(className);
                        transformClass(ctClass);
                        ctClass.writeFile(outputDir.getAbsolutePath());
                    }
                }
            }
        } catch (NotFoundException | CannotCompileException e) {
            throw new RuntimeException(e);
        }
    }

    private List<File> getChangedClassesList(final DirectoryInput directoryInput) throws IOException {
        final Map<File, Status> changedFiles = directoryInput.getChangedFiles();
        if (changedFiles.isEmpty()) {
            // we're in non incremental mode
            return Files.walk(directoryInput.getFile().toPath())
                    .filter(Files::isRegularFile)
                    .map(Path::toFile)
                    .collect(Collectors.toList());
        } else {
            changedFiles.entrySet().stream()
                    .filter(it -> it.getValue() == Status.REMOVED)
                    .forEach(it -> it.getKey().delete());

            return changedFiles.entrySet().stream()
                    .filter(it -> it.getValue() == Status.ADDED || it.getValue() == Status.CHANGED)
                    .map(Map.Entry::getKey)
                    .filter(File::isFile)
                    .collect(Collectors.toList());
        }
    }

    // Imports substitution happens here
    private void transformClass(final CtClass ctClass) {
        ClassFile classFile = ctClass.getClassFile();
        ConstPool constPool = classFile.getConstPool();
        constPool.renameClass(classTransformTable);
    }

    private String filePathToClassname(File file) {
        return file.getPath().replace("/", ".")
                .replace("\\", ".")
                .replace(".class", "");
    }
}