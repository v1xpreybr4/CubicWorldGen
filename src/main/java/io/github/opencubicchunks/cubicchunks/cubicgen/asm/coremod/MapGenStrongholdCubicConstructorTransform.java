/*
 *  This file is part of Cubic World Generation, licensed under the MIT License (MIT).
 *
 *  Copyright (c) 2015 contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package io.github.opencubicchunks.cubicchunks.cubicgen.asm.coremod;

import io.github.opencubicchunks.cubicchunks.cubicgen.asm.Mappings;
import jdk.internal.org.objectweb.asm.Opcodes;
import net.minecraft.launchwrapper.IClassTransformer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

public class MapGenStrongholdCubicConstructorTransform implements IClassTransformer {
    private static final Logger LOGGER = LogManager.getLogger();
    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) return null;
        if ("net.minecraft.world.gen.structure.MapGenStronghold$Start".equals(transformedName)) {
            ClassReader cr = new ClassReader(basicClass);
            ClassNode node = new ClassNode();
            cr.accept(node, 0);
            applyTransform(node);
            ClassWriter cw = new ClassWriter(0);
            node.accept(cw);
            return cw.toByteArray();
        }
        return basicClass;
    }

    private void applyTransform(ClassNode node) {
        LOGGER.info("Transforming class {}: adding reinitCubicStronghold method as copy of constructor", node.name);
        String markAvailableHeight = Mappings.getNameFromSrg("func_75067_a");
        String components = Mappings.getNameFromSrg("field_75075_a");
        String boundingBox = Mappings.getNameFromSrg("field_75074_b");
        LOGGER.debug("markAvailableHeight method name: {}", markAvailableHeight);
        LOGGER.debug("components field name: {}", components);
        LOGGER.debug("boundingBox field name: {}", boundingBox);

        String structureStart = "net/minecraft/world/gen/structure/StructureStart";
        String structureBoundingBoxDesc = "Lnet/minecraft/world/gen/structure/StructureBoundingBox;";
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC,
                "cubicchunks$baseY", Type.getDescriptor(int.class), null, 0));

        MethodNode createdMethod = null;
        for (MethodNode method : node.methods) {
            if (method.name.equals("<init>") && method.desc.equals("(Lnet/minecraft/world/World;Ljava/util/Random;II)V")) {
                LOGGER.debug("Found target constructor: {}{}", method.name, method.desc);
                MethodNode newMethod = new MethodNode(method.access, "reinitCubicStronghold",
                        "(Lnet/minecraft/world/World;Ljava/util/Random;II)V", null, null);
                MethodVisitor mv = new MethodVisitor(Opcodes.ASM5, newMethod) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                        // note: none of these transformation require any stack map frames changes or maxs changes
                        // so they are not recomputed
                        if (opcode == Opcodes.INVOKESPECIAL && name.equals("<init>") && owner.equals(structureStart)) {
                            LOGGER.debug("Replacing superconstructor call: {}{}", name, desc);
                            Type[] types = Type.getArgumentTypes(desc);
                            // remove superconstructor call, pop parameters
                            for (Type type : types) {
                                super.visitInsn(type.getSize() == 1 ? Opcodes.POP : Opcodes.POP2);
                            }
                            // keep "this"
                            // clear components
                            // no need to ALOAD 0, kept from superconstructor call
                            super.visitTypeInsn(Opcodes.CHECKCAST, structureStart);
                            super.visitFieldInsn(Opcodes.GETFIELD, structureStart, components, Type.getDescriptor(List.class));
                            super.visitMethodInsn(Opcodes.INVOKEINTERFACE, Type.getInternalName(List.class), "clear", "()V", true);
                            // set bounding box to null
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitTypeInsn(Opcodes.CHECKCAST, structureStart);
                            super.visitInsn(Opcodes.ACONST_NULL);
                            super.visitFieldInsn(Opcodes.PUTFIELD, structureStart, boundingBox, structureBoundingBoxDesc);
                        } else if (opcode == Opcodes.INVOKEVIRTUAL && name.equals(markAvailableHeight)) {
                            LOGGER.debug("Replacing constant argument for markAvailableHeight with GETFIELD cubicchunks$baseY");
                            // pop constant 10
                            super.visitInsn(Opcodes.POP);
                            // load value from field
                            super.visitVarInsn(Opcodes.ALOAD, 0);
                            super.visitFieldInsn(Opcodes.GETFIELD, owner, "cubicchunks$baseY", Type.getDescriptor(int.class));
                            // call method with modified value
                            super.visitMethodInsn(opcode, owner, name, desc, itf);
                        } else {
                            super.visitMethodInsn(opcode, owner, name, desc, itf);
                        }
                    }
                };
                method.accept(mv);
                createdMethod = newMethod;
                break;
            }
        }
        if (createdMethod == null) {
            throw new RuntimeException("net.minecraft.world.gen.structure.MapGenStronghold$Start constructor with descriptor (Lnet/minecraft/world/World;Ljava/util/Random;II)V not found! This should not be possible.");
        }
        node.methods.add(createdMethod);
    }
}
