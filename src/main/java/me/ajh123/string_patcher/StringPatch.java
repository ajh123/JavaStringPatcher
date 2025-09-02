package me.ajh123.string_patcher;

import java.io.*;
import java.nio.file.*;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import me.mrletsplay.mrcore.misc.classfile.ClassFile;
import me.mrletsplay.mrcore.misc.classfile.pool.entry.ConstantPoolEntry;
import me.mrletsplay.mrcore.misc.classfile.pool.entry.ConstantPoolStringEntry;
import me.mrletsplay.mrcore.misc.classfile.util.ClassFileUtils;
import me.mrletsplay.shittyauthpatcher.patch.Patch;
import me.mrletsplay.shittyauthpatcher.util.PatchingException;

public class StringPatch implements Patch {
    private static final String INPUT = "input";
    private static final String OUTPUT = "output";
    private static final String ORIGINAL_TEXT = "original-text";
    private static final String OUTPUT_TEXT = "output-text";
    private static final String CLASS_PATH = "class-path";

    public StringPatch() {
    }

    public String getDescription() {
        return "Patcher that replaces strings in a class file inside a jar";
    }

    public OptionParser createParser() {
        OptionParser parser = Patch.createBaseParser();
        parser.accepts(INPUT, "Path to the input jar file").withRequiredArg().ofType(File.class).required();
        parser.accepts(OUTPUT, "Output file for the patched jar").withRequiredArg().ofType(File.class);
        parser.accepts(ORIGINAL_TEXT, "The original text to replace").withRequiredArg().ofType(String.class);
        parser.accepts(OUTPUT_TEXT, "The new text to replace the original").withRequiredArg().ofType(String.class);
        parser.accepts(CLASS_PATH, "The path to the class file inside the jar to patch (e.g. com/example/Main.class)").withRequiredArg().ofType(String.class);
        return parser;
    }

    public void patch(OptionSet options) throws Exception {
        File inputFile = (File)options.valueOf(INPUT);
        String originalText = (String)options.valueOf(ORIGINAL_TEXT);
        String outputText = (String)options.valueOf(OUTPUT_TEXT);
        String classPath = (String)options.valueOf(CLASS_PATH);

        if (!inputFile.exists()) {
            throw new FileNotFoundException(inputFile.getAbsolutePath());
        }

        File out = inputFile;
        if (options.has(OUTPUT)) {
            out = (File)options.valueOf(OUTPUT);
        }

        System.out.println("Output for patcher: " + out.getAbsolutePath());
        doPatch(inputFile.toPath(), out.toPath(), originalText, outputText, classPath);
    }

    public static void doPatch(Path inputLib, Path outputFile, String originalText, String outputText, String classPath) throws IOException, PatchingException {
        System.out.println("Patching jar");
        if (!outputFile.equals(inputLib)) {
            Files.copy(inputLib, outputFile, StandardCopyOption.REPLACE_EXISTING);
        }

        try (FileSystem fs = FileSystems.newFileSystem(outputFile, (ClassLoader)null)) {

            Path environment = fs.getPath(classPath);
            if (Files.exists(environment)) {
                System.out.println("Patching "+classPath);

                ClassFile environmentClass;
                try (InputStream in = Files.newInputStream(environment)) {
                    environmentClass = new ClassFile(in);
                }

                replaceStrings(environmentClass, originalText, outputText);

                try (OutputStream out = Files.newOutputStream(environment)) {
                    environmentClass.write(out);
                }
            }
        }

        System.out.println("Done patching jar!");
    }

    private static void replaceStrings(ClassFile cf, String find, String replace) {
        for(int i = 1; i < cf.getConstantPool().getSize() + 1; ++i) {
            ConstantPoolEntry e = cf.getConstantPool().getEntry(i);
            if (e instanceof ConstantPoolStringEntry) {
                String s = e.as(ConstantPoolStringEntry.class).getString().getValue();
                if (s.startsWith(find)) {
                    cf.getConstantPool().setEntry(i, new ConstantPoolStringEntry(cf.getConstantPool(), ClassFileUtils.getOrAppendUTF8(cf, s.replace(find, replace))));
                }
            }
        }
    }
}