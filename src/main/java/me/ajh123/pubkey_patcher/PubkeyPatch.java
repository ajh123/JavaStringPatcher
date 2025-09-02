package me.ajh123.pubkey_patcher;

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

public class PubkeyPatch implements Patch {
    private static final String INPUT = "input";
    private static final String OUTPUT = "output";
    private static final String PUBLIC_KEY = "public-key";
    private static final String NEW_PUBLIC_KEY = "new-public-key";

    public PubkeyPatch() {
    }

    public String getDescription() {
        return "Public key file jar patch";
    }

    public OptionParser createParser() {
        OptionParser parser = Patch.createBaseParser();
        parser.accepts(INPUT, "Path to the input jar file").withRequiredArg().ofType(File.class).required();
        parser.accepts(OUTPUT, "Output file for the patched jar").withRequiredArg().ofType(File.class);
        parser.accepts(PUBLIC_KEY, "Path to the public key file to replace. Must be in X.509 SubjectPublicKeyInfo format (without headers)").withRequiredArg().ofType(File.class);
        parser.accepts(NEW_PUBLIC_KEY, "Path to the public key file to use. Must be in X.509 SubjectPublicKeyInfo format (without headers)").withRequiredArg().ofType(File.class);
        Patch.requireKey(parser);
        return parser;
    }

    public void patch(OptionSet options) throws Exception {
        File serverFile = (File)options.valueOf(INPUT);
        File pubKey = (File)options.valueOf(PUBLIC_KEY);
        File newPublicKey = (File)options.valueOf(NEW_PUBLIC_KEY);


        if (!serverFile.exists()) {
            throw new FileNotFoundException(serverFile.getAbsolutePath());
        }
        if (!pubKey.exists()) {
            throw new FileNotFoundException(pubKey.getAbsolutePath());
        }
        if (!newPublicKey.exists()) {
            throw new FileNotFoundException(newPublicKey.getAbsolutePath());
        }

        File out = serverFile;
        if (options.has(OUTPUT)) {
            out = (File)options.valueOf(OUTPUT);
        }

        System.out.println("Output for axiom: " + out.getAbsolutePath());
        doPatch(serverFile.toPath(), out.toPath(), newPublicKey, pubKey);
    }

    public static void doPatch(Path inputLib, Path outputFile, File newPublicKey, File originalPubKey) throws IOException, PatchingException {
        System.out.println("Patching jar");
        if (!outputFile.equals(inputLib)) {
            Files.copy(inputLib, outputFile, StandardCopyOption.REPLACE_EXISTING);
        }

        try (FileSystem fs = FileSystems.newFileSystem(outputFile, (ClassLoader)null)) {

            Path environment = fs.getPath("com/moulberry/axiom/utils/Authorization.class");
            if (Files.exists(environment)) {
                System.out.println("Patching Authorization.class");

                ClassFile environmentClass;
                try (InputStream in = Files.newInputStream(environment)) {
                    environmentClass = new ClassFile(in);
                }

                if (newPublicKey != null && newPublicKey.exists() && originalPubKey != null && originalPubKey.exists()) {
                    String origKey = new String(Files.readAllBytes(originalPubKey.toPath())).replace("\n", "").replace("\r", "");
                    String pubKey = new String(Files.readAllBytes(newPublicKey.toPath())).replace("\n", "").replace("\r", "");
                    System.out.println("Copying public key from " + newPublicKey.getAbsolutePath());
                    replaceStrings(environmentClass, origKey, pubKey);
                } else {
                    System.out.println("No public key provided or key file doesn't exist. Skipping");
                }

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