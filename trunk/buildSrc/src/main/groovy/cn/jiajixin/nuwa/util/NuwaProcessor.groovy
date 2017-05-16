package cn.jiajixin.nuwa.util

import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.IOUtils
import org.objectweb.asm.*

import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

class NuwaProcessor {

    public static processJar(File hashFile, File jarFile, File patchDir, Map map, HashSet<String> includePackage, HashSet<String> excludePackage, HashSet<String> excludeClass) {
        if (jarFile) {

            println("processJar:" + jarFile.absolutePath)
            def optJar = new File(jarFile.getParent(), jarFile.name + ".opt")

            def file = new JarFile(jarFile);
            Enumeration enumeration = file.entries();
            JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(optJar));

            while (enumeration.hasMoreElements()) {
                JarEntry jarEntry = (JarEntry) enumeration.nextElement();
                String entryName = jarEntry.getName();
                ZipEntry zipEntry = new ZipEntry(entryName);

                jarOutputStream.putNextEntry(zipEntry);
                InputStream inputStream = null;

                if (shouldProcessClassInJar(entryName, includePackage, excludePackage, excludeClass)) {
                    println("xxxxxxxxxxxxxxxxxxxxreferHackWhenInit1: " + entryName)
                    inputStream = file.getInputStream(jarEntry);
                    def bytes = referHackWhenInit(inputStream);
                    if(bytes != null)//不知道什么原因，有些第三方sdk里面的类读取异常
                    {
                        jarOutputStream.write(bytes);

                        def hash = DigestUtils.shaHex(bytes)
                        hashFile.append(NuwaMapUtils.format(entryName, hash))

                        if (NuwaMapUtils.notSame(map, entryName, hash)) {//这里定义changeClass列表变量，在这列表中的认为是改动过的
                            println("processJar.patch file: " + entryName)
                            NuwaFileUtils.copyBytesToFile(bytes, NuwaFileUtils.touchFile(patchDir, entryName))
                        }
                    }
                    else {
                        inputStream = file.getInputStream(jarEntry);
                        jarOutputStream.write(IOUtils.toByteArray(inputStream));
                        inputStream.close();
                    }
                } else {
                    inputStream = file.getInputStream(jarEntry);
                    jarOutputStream.write(IOUtils.toByteArray(inputStream));
                    inputStream.close();
                }
                jarOutputStream.closeEntry();
            }
            jarOutputStream.close();
            file.close();

            if (jarFile.exists()) {
                jarFile.delete()
            }
            optJar.renameTo(jarFile)
        }
    }

    public static void processClassPath(String dirName, File hashFile, File classPath, File patchDir, Map map, HashSet<String> includePackage, HashSet<String> excludePackage, HashSet<String> excludeClass) {
        File[] classfiles = classPath.listFiles()
        classfiles.each { inputFile ->
            def path = inputFile.absolutePath
            path = path.replaceAll("\\\\", "/");
            path = path.split("${dirName}/")[1]
            if (inputFile.isDirectory()) {
                processClassPath(dirName, hashFile, inputFile, patchDir, map, includePackage, excludePackage, excludeClass)
            } else if (path.endsWith(".jar")) {
                NuwaProcessor.processJar(hashFile, inputFile, patchDir, map, includePackage, excludePackage, excludeClass)
            } else if (path.endsWith(".class") && !path.contains("/R\$") && !path.endsWith("/R.class") && !path.endsWith("/BuildConfig.class")) {
                if (NuwaSetUtils.isIncluded(path, includePackage)) {
                    if (!NuwaSetUtils.isExcluded(path, excludePackage, excludeClass)) {
                        def bytes = NuwaProcessor.processClass(inputFile)
                        def hash = DigestUtils.shaHex(bytes)
                        hashFile.append(NuwaMapUtils.format(path, hash))
                        if (NuwaMapUtils.notSame(map, path, hash)) {//这里定义changeClass列表变量，在这列表中的认为是改动过的
                            println("processClassPath.patch file: " + path)
                            NuwaFileUtils.copyBytesToFile(inputFile.bytes, NuwaFileUtils.touchFile(patchDir, path))
                        }
                    }
                }
            }

        }
    }

    private static class InjectClassVisitor extends ClassVisitor {

        InjectClassVisitor(int i, ClassVisitor classVisitor) {
            super(i, classVisitor)
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc,
                                         String signature, String[] exceptions) {

            MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
            mv = new MethodVisitor(Opcodes.ASM4, mv) {
                @Override
                void visitInsn(int opcode) {
                    if ("<init>".equals(name) && opcode == Opcodes.RETURN) {
                        Label l1 = new Label();
                        super.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/Boolean", "FALSE", "Ljava/lang/Boolean;");
                        super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
                        super.visitJumpInsn(Opcodes.IFEQ, l1);
                        super.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
                        super.visitLdcInsn(Type.getType("Lcn/jiajixin/nuwa/Hack;"));
                        super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/Object;)V", false);
                        super.visitLabel(l1);
                    }
//                    if ("<init>".equals(name) && opcode == Opcodes.RETURN) {
//                        super.visitLdcInsn(Type.getType("Lcn/jiajixin/nuwa/Hack;"));
//                    }
                    super.visitInsn(opcode);
                }

                @Override
                public void visitMaxs(int maxStack, int maxLocal) {
                    if ("<init>".equals(name)) {
                        super.visitMaxs(maxStack + 2, maxLocal);
                    } else {
                        super.visitMaxs(maxStack, maxLocal);
                    }
                }
            }
            return mv;
        }
    }

    //refer hack class when object init
    private static byte[] referHackWhenInit(InputStream inputStream) {
        try {
            ClassReader cr = new ClassReader(inputStream);
            ClassWriter cw = new ClassWriter(cr, 0);

            ClassVisitor cv = new InjectClassVisitor(Opcodes.ASM4, cw);
            cr.accept(cv, 0);
            return cw.toByteArray();
        }
        catch (Exception e)
        {
            e.printStackTrace()
            return null;
        }
    }

    private static boolean shouldProcessClassInJar(String entryName, HashSet<String> includePackage, HashSet<String> excludePackage, HashSet<String> excludeClass) {
//        entryName = entryName.replaceAll("\\\\", "/");
        if (!entryName.endsWith(".class")) {
            return false;
        }
        if (entryName.contains("/R\$") || entryName.endsWith("/R.class") || entryName.endsWith("/BuildConfig.class") || entryName.startsWith("cn/jiajixin/nuwa/") || entryName.contains("android/support/"))
        {
            return false;
        }
        return NuwaSetUtils.isIncluded(entryName, includePackage) && !NuwaSetUtils.isExcluded(entryName, excludePackage, excludeClass)
    }

    public static byte[] processClass(File file) {
        def optClass = new File(file.getParent(), file.name + ".opt")

        FileInputStream inputStream = new FileInputStream(file);
        FileOutputStream outputStream = new FileOutputStream(optClass)
//        println("xxxxxxxxxxxxxxxxxxxxreferHackWhenInit2: " + file.absolutePath)

        def bytes = referHackWhenInit(inputStream);
        outputStream.write(bytes)
        inputStream.close()
        outputStream.close()
        if (file.exists()) {
            file.delete()
        }
        optClass.renameTo(file)
        return bytes
    }
}
