/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 Igor Konev
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jephyr.easyflow.instrument;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AnalyzerAdapter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.objectweb.asm.Opcodes.AALOAD;
import static org.objectweb.asm.Opcodes.AASTORE;
import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ANEWARRAY;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ARRAYLENGTH;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.ATHROW;
import static org.objectweb.asm.Opcodes.BALOAD;
import static org.objectweb.asm.Opcodes.BASTORE;
import static org.objectweb.asm.Opcodes.BIPUSH;
import static org.objectweb.asm.Opcodes.CALOAD;
import static org.objectweb.asm.Opcodes.CASTORE;
import static org.objectweb.asm.Opcodes.CHECKCAST;
import static org.objectweb.asm.Opcodes.D2F;
import static org.objectweb.asm.Opcodes.D2I;
import static org.objectweb.asm.Opcodes.D2L;
import static org.objectweb.asm.Opcodes.DADD;
import static org.objectweb.asm.Opcodes.DALOAD;
import static org.objectweb.asm.Opcodes.DASTORE;
import static org.objectweb.asm.Opcodes.DCMPG;
import static org.objectweb.asm.Opcodes.DCMPL;
import static org.objectweb.asm.Opcodes.DCONST_0;
import static org.objectweb.asm.Opcodes.DCONST_1;
import static org.objectweb.asm.Opcodes.DDIV;
import static org.objectweb.asm.Opcodes.DLOAD;
import static org.objectweb.asm.Opcodes.DMUL;
import static org.objectweb.asm.Opcodes.DNEG;
import static org.objectweb.asm.Opcodes.DOUBLE;
import static org.objectweb.asm.Opcodes.DREM;
import static org.objectweb.asm.Opcodes.DRETURN;
import static org.objectweb.asm.Opcodes.DSTORE;
import static org.objectweb.asm.Opcodes.DSUB;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.DUP2;
import static org.objectweb.asm.Opcodes.DUP2_X1;
import static org.objectweb.asm.Opcodes.DUP2_X2;
import static org.objectweb.asm.Opcodes.DUP_X1;
import static org.objectweb.asm.Opcodes.DUP_X2;
import static org.objectweb.asm.Opcodes.F2D;
import static org.objectweb.asm.Opcodes.F2I;
import static org.objectweb.asm.Opcodes.F2L;
import static org.objectweb.asm.Opcodes.FADD;
import static org.objectweb.asm.Opcodes.FALOAD;
import static org.objectweb.asm.Opcodes.FASTORE;
import static org.objectweb.asm.Opcodes.FCMPG;
import static org.objectweb.asm.Opcodes.FCMPL;
import static org.objectweb.asm.Opcodes.FCONST_0;
import static org.objectweb.asm.Opcodes.FCONST_1;
import static org.objectweb.asm.Opcodes.FCONST_2;
import static org.objectweb.asm.Opcodes.FDIV;
import static org.objectweb.asm.Opcodes.FLOAD;
import static org.objectweb.asm.Opcodes.FLOAT;
import static org.objectweb.asm.Opcodes.FMUL;
import static org.objectweb.asm.Opcodes.FNEG;
import static org.objectweb.asm.Opcodes.FREM;
import static org.objectweb.asm.Opcodes.FRETURN;
import static org.objectweb.asm.Opcodes.FSTORE;
import static org.objectweb.asm.Opcodes.FSUB;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.GETSTATIC;
import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.Opcodes.I2B;
import static org.objectweb.asm.Opcodes.I2C;
import static org.objectweb.asm.Opcodes.I2D;
import static org.objectweb.asm.Opcodes.I2F;
import static org.objectweb.asm.Opcodes.I2L;
import static org.objectweb.asm.Opcodes.I2S;
import static org.objectweb.asm.Opcodes.IADD;
import static org.objectweb.asm.Opcodes.IALOAD;
import static org.objectweb.asm.Opcodes.IAND;
import static org.objectweb.asm.Opcodes.IASTORE;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.ICONST_1;
import static org.objectweb.asm.Opcodes.ICONST_2;
import static org.objectweb.asm.Opcodes.ICONST_3;
import static org.objectweb.asm.Opcodes.ICONST_4;
import static org.objectweb.asm.Opcodes.ICONST_5;
import static org.objectweb.asm.Opcodes.ICONST_M1;
import static org.objectweb.asm.Opcodes.IDIV;
import static org.objectweb.asm.Opcodes.IFEQ;
import static org.objectweb.asm.Opcodes.IFGE;
import static org.objectweb.asm.Opcodes.IFGT;
import static org.objectweb.asm.Opcodes.IFLE;
import static org.objectweb.asm.Opcodes.IFLT;
import static org.objectweb.asm.Opcodes.IFNE;
import static org.objectweb.asm.Opcodes.IFNULL;
import static org.objectweb.asm.Opcodes.IF_ACMPEQ;
import static org.objectweb.asm.Opcodes.IF_ACMPNE;
import static org.objectweb.asm.Opcodes.IF_ICMPEQ;
import static org.objectweb.asm.Opcodes.IF_ICMPGE;
import static org.objectweb.asm.Opcodes.IF_ICMPGT;
import static org.objectweb.asm.Opcodes.IF_ICMPLE;
import static org.objectweb.asm.Opcodes.IF_ICMPLT;
import static org.objectweb.asm.Opcodes.IF_ICMPNE;
import static org.objectweb.asm.Opcodes.IINC;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.IMUL;
import static org.objectweb.asm.Opcodes.INEG;
import static org.objectweb.asm.Opcodes.INSTANCEOF;
import static org.objectweb.asm.Opcodes.INTEGER;
import static org.objectweb.asm.Opcodes.INVOKEDYNAMIC;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.IOR;
import static org.objectweb.asm.Opcodes.IREM;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.ISHL;
import static org.objectweb.asm.Opcodes.ISHR;
import static org.objectweb.asm.Opcodes.ISTORE;
import static org.objectweb.asm.Opcodes.ISUB;
import static org.objectweb.asm.Opcodes.IUSHR;
import static org.objectweb.asm.Opcodes.IXOR;
import static org.objectweb.asm.Opcodes.JSR;
import static org.objectweb.asm.Opcodes.L2D;
import static org.objectweb.asm.Opcodes.L2F;
import static org.objectweb.asm.Opcodes.L2I;
import static org.objectweb.asm.Opcodes.LADD;
import static org.objectweb.asm.Opcodes.LALOAD;
import static org.objectweb.asm.Opcodes.LAND;
import static org.objectweb.asm.Opcodes.LASTORE;
import static org.objectweb.asm.Opcodes.LCMP;
import static org.objectweb.asm.Opcodes.LCONST_0;
import static org.objectweb.asm.Opcodes.LCONST_1;
import static org.objectweb.asm.Opcodes.LDC;
import static org.objectweb.asm.Opcodes.LDIV;
import static org.objectweb.asm.Opcodes.LLOAD;
import static org.objectweb.asm.Opcodes.LMUL;
import static org.objectweb.asm.Opcodes.LNEG;
import static org.objectweb.asm.Opcodes.LONG;
import static org.objectweb.asm.Opcodes.LOOKUPSWITCH;
import static org.objectweb.asm.Opcodes.LOR;
import static org.objectweb.asm.Opcodes.LREM;
import static org.objectweb.asm.Opcodes.LRETURN;
import static org.objectweb.asm.Opcodes.LSHL;
import static org.objectweb.asm.Opcodes.LSHR;
import static org.objectweb.asm.Opcodes.LSTORE;
import static org.objectweb.asm.Opcodes.LSUB;
import static org.objectweb.asm.Opcodes.LUSHR;
import static org.objectweb.asm.Opcodes.LXOR;
import static org.objectweb.asm.Opcodes.MONITORENTER;
import static org.objectweb.asm.Opcodes.MONITOREXIT;
import static org.objectweb.asm.Opcodes.MULTIANEWARRAY;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.NEWARRAY;
import static org.objectweb.asm.Opcodes.NOP;
import static org.objectweb.asm.Opcodes.NULL;
import static org.objectweb.asm.Opcodes.POP;
import static org.objectweb.asm.Opcodes.POP2;
import static org.objectweb.asm.Opcodes.PUTFIELD;
import static org.objectweb.asm.Opcodes.PUTSTATIC;
import static org.objectweb.asm.Opcodes.RET;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.SALOAD;
import static org.objectweb.asm.Opcodes.SASTORE;
import static org.objectweb.asm.Opcodes.SIPUSH;
import static org.objectweb.asm.Opcodes.SWAP;
import static org.objectweb.asm.Opcodes.TABLESWITCH;
import static org.objectweb.asm.Opcodes.TOP;

final class NewRelocatorMethodAdapter extends AnalyzingMethodNode {

    private final MethodVisitor mv;

    private NewRelocatorMethodAdapter(int access, String name, String desc, String signature, String[] exceptions,
            MethodVisitor mv) {
        super(access, name, desc, signature, exceptions);
        this.mv = mv;
    }

    static MethodVisitor create(String owner, int access, String name, String desc, String signature,
            String[] exceptions, MethodVisitor mv) {
        NewRelocatorMethodAdapter adapter =
                new NewRelocatorMethodAdapter(access, name, desc, signature, exceptions, mv);
        AnalyzerAdapter analyzerAdapter = new AnalyzerAdapter(owner, access, name, desc, adapter);
        adapter.adapter = analyzerAdapter;
        return analyzerAdapter;
    }

    @Override
    public void visitEnd() {
        AbstractInsnNode next = instructions.getFirst();
        boolean removeFrame = false;
        int stackSize = 0;

        while (next != null) {
            AbstractInsnNode node = next;
            next = next.getNext();
            Object[] stack;

            switch (node.getOpcode()) {
                case -1:
                    if (node instanceof FrameNode) {
                        if (removeFrame) {
                            instructions.remove(node);
                        } else {
                            stackSize = handleFrame((FrameNode) node);
                            removeFrame = true;
                        }
                    }
                    break;
                case NOP:
                case LALOAD:
                case DALOAD:
                case INEG:
                case LNEG:
                case FNEG:
                case DNEG:
                case IINC:
                case I2F:
                case L2D:
                case F2I:
                case D2L:
                case I2B:
                case I2C:
                case I2S:
                case GOTO:
                case RET:
                case NEWARRAY:
                case ANEWARRAY:
                case ARRAYLENGTH:
                case CHECKCAST:
                case INSTANCEOF:
                    removeFrame = false;
                    break;
                case ACONST_NULL:
                case ICONST_M1:
                case ICONST_0:
                case ICONST_1:
                case ICONST_2:
                case ICONST_3:
                case ICONST_4:
                case ICONST_5:
                case FCONST_0:
                case FCONST_1:
                case FCONST_2:
                case BIPUSH:
                case SIPUSH:
                case ILOAD:
                case FLOAD:
                case I2L:
                case I2D:
                case F2L:
                case F2D:
                case JSR:
                    stackSize += 1;
                    updateMaxStack(stackSize);
                    removeFrame = false;
                    break;
                case LCONST_0:
                case LCONST_1:
                case DCONST_0:
                case DCONST_1:
                case LLOAD:
                case DLOAD:
                    stackSize += 2;
                    updateMaxStack(stackSize);
                    removeFrame = false;
                    break;
                case LDC:
                    Object cst = ((LdcInsnNode) node).cst;
                    stackSize += cst instanceof Long || cst instanceof Double ? 2 : 1;
                    updateMaxStack(stackSize);
                    removeFrame = false;
                    break;
                case ALOAD:
                    if (frames.get(node).locals[((VarInsnNode) node).var] instanceof AbstractInsnNode) {
                        instructions.remove(node);
                    } else {
                        stackSize += 1;
                        updateMaxStack(stackSize);
                        removeFrame = false;
                    }
                    break;
                case IALOAD:
                case FALOAD:
                case AALOAD:
                case BALOAD:
                case CALOAD:
                case SALOAD:
                case ISTORE:
                case FSTORE:
                case IADD:
                case FADD:
                case ISUB:
                case FSUB:
                case IMUL:
                case FMUL:
                case IDIV:
                case FDIV:
                case IREM:
                case FREM:
                case ISHL:
                case ISHR:
                case IUSHR:
                case IAND:
                case IOR:
                case IXOR:
                case L2I:
                case L2F:
                case D2I:
                case D2F:
                case IFEQ:
                case IFNE:
                case IFLT:
                case IFGE:
                case IFGT:
                case IFLE:
                case TABLESWITCH:
                case LOOKUPSWITCH:
                case MONITORENTER:
                case MONITOREXIT:
                    stackSize -= 1;
                    removeFrame = false;
                    break;
                case LSTORE:
                case DSTORE:
                case LADD:
                case DADD:
                case LSUB:
                case DSUB:
                case LMUL:
                case DMUL:
                case LDIV:
                case DDIV:
                case LREM:
                case DREM:
                case LSHL:
                case LSHR:
                case LUSHR:
                case LAND:
                case LOR:
                case LXOR:
                case FCMPL:
                case FCMPG:
                case IF_ICMPEQ:
                case IF_ICMPNE:
                case IF_ICMPLT:
                case IF_ICMPGE:
                case IF_ICMPGT:
                case IF_ICMPLE:
                    stackSize -= 2;
                    removeFrame = false;
                    break;
                case ASTORE:
                    stack = frames.get(node).stack;
                    if (stack[stack.length - 1] instanceof AbstractInsnNode) {
                        instructions.remove(node);
                    } else {
                        stackSize -= 1;
                        removeFrame = false;
                    }
                    break;
                case IASTORE:
                case FASTORE:
                case AASTORE:
                case BASTORE:
                case CASTORE:
                case SASTORE:
                case LCMP:
                case DCMPL:
                case DCMPG:
                    stackSize -= 3;
                    removeFrame = false;
                    break;
                case LASTORE:
                case DASTORE:
                    stackSize -= 4;
                    removeFrame = false;
                    break;
                case POP:
                    stack = frames.get(node).stack;
                    if (stack[stack.length - 1] instanceof AbstractInsnNode) {
                        instructions.remove(node);
                    } else {
                        stackSize -= 1;
                        removeFrame = false;
                    }
                    break;
                case POP2:
                    stack = frames.get(node).stack;
                    if (stack[stack.length - 1] instanceof AbstractInsnNode) {
                        if (stack[stack.length - 2] instanceof AbstractInsnNode) {
                            instructions.remove(node);
                        } else {
                            instructions.set(node, new InsnNode(POP));
                            stackSize -= 1;
                            removeFrame = false;
                        }
                    } else {
                        if (stack[stack.length - 2] instanceof AbstractInsnNode) {
                            instructions.set(node, new InsnNode(POP));
                            stackSize -= 1;
                        } else {
                            stackSize -= 2;
                        }
                        removeFrame = false;
                    }
                    break;
                case DUP:
                    stack = frames.get(node).stack;
                    if (stack[stack.length - 1] instanceof AbstractInsnNode) {
                        instructions.remove(node);
                    } else {
                        stackSize += 1;
                        updateMaxStack(stackSize);
                        removeFrame = false;
                    }
                    break;
                case DUP_X1:
                    stack = frames.get(node).stack;
                    if (stack[stack.length - 1] instanceof AbstractInsnNode) {
                        instructions.remove(node);
                    } else {
                        if (stack[stack.length - 2] instanceof AbstractInsnNode) {
                            instructions.set(node, new InsnNode(DUP));
                        }
                        stackSize += 1;
                        updateMaxStack(stackSize);
                        removeFrame = false;
                    }
                    break;
                case DUP_X2:
                    stack = frames.get(node).stack;
                    if (stack[stack.length - 1] instanceof AbstractInsnNode) {
                        instructions.remove(node);
                    } else {
                        if (stack[stack.length - 2] instanceof AbstractInsnNode) {
                            if (stack[stack.length - 3] instanceof AbstractInsnNode) {
                                instructions.set(node, new InsnNode(DUP));
                            } else {
                                instructions.set(node, new InsnNode(DUP_X1));
                            }
                        } else {
                            if (stack[stack.length - 3] instanceof AbstractInsnNode) {
                                instructions.set(node, new InsnNode(DUP_X1));
                            }
                        }
                        stackSize += 1;
                        updateMaxStack(stackSize);
                        removeFrame = false;
                    }
                    break;
                case DUP2:
                    stack = frames.get(node).stack;
                    if (stack[stack.length - 1] instanceof AbstractInsnNode) {
                        if (stack[stack.length - 2] instanceof AbstractInsnNode) {
                            instructions.remove(node);
                        } else {
                            instructions.set(node, new InsnNode(DUP));
                            stackSize += 1;
                            updateMaxStack(stackSize);
                            removeFrame = false;
                        }
                    } else {
                        if (stack[stack.length - 2] instanceof AbstractInsnNode) {
                            instructions.set(node, new InsnNode(DUP));
                            stackSize += 1;
                        } else {
                            stackSize += 2;
                        }
                        updateMaxStack(stackSize);
                        removeFrame = false;
                    }
                    break;
                case DUP2_X1:
                    stack = frames.get(node).stack;
                    if (stack[stack.length - 1] instanceof AbstractInsnNode) {
                        if (stack[stack.length - 2] instanceof AbstractInsnNode) {
                            instructions.remove(node);
                        } else {
                            if (stack[stack.length - 3] instanceof AbstractInsnNode) {
                                instructions.set(node, new InsnNode(DUP));
                            } else {
                                instructions.set(node, new InsnNode(DUP_X1));
                            }
                            stackSize += 1;
                            updateMaxStack(stackSize);
                            removeFrame = false;
                        }
                    } else {
                        if (stack[stack.length - 2] instanceof AbstractInsnNode) {
                            if (stack[stack.length - 3] instanceof AbstractInsnNode) {
                                instructions.set(node, new InsnNode(DUP));
                            } else {
                                instructions.set(node, new InsnNode(DUP_X1));
                            }
                            stackSize += 1;
                        } else {
                            if (stack[stack.length - 3] instanceof AbstractInsnNode) {
                                instructions.set(node, new InsnNode(DUP2));
                            }
                            stackSize += 2;
                        }
                        updateMaxStack(stackSize);
                        removeFrame = false;
                    }
                    break;
                case DUP2_X2:
                    stack = frames.get(node).stack;
                    if (stack[stack.length - 1] instanceof AbstractInsnNode) {
                        if (stack[stack.length - 2] instanceof AbstractInsnNode) {
                            instructions.remove(node);
                        } else {
                            if (stack[stack.length - 3] instanceof AbstractInsnNode) {
                                if (stack[stack.length - 4] instanceof AbstractInsnNode) {
                                    instructions.set(node, new InsnNode(DUP));
                                } else {
                                    instructions.set(node, new InsnNode(DUP_X1));
                                }
                            } else {
                                if (stack[stack.length - 3] instanceof AbstractInsnNode) {
                                    instructions.set(node, new InsnNode(DUP_X1));
                                } else {
                                    instructions.set(node, new InsnNode(DUP_X2));
                                }
                            }
                            stackSize += 1;
                            updateMaxStack(stackSize);
                            removeFrame = false;
                        }
                    } else {
                        if (stack[stack.length - 2] instanceof AbstractInsnNode) {
                            if (stack[stack.length - 3] instanceof AbstractInsnNode) {
                                if (stack[stack.length - 4] instanceof AbstractInsnNode) {
                                    instructions.set(node, new InsnNode(DUP));
                                } else {
                                    instructions.set(node, new InsnNode(DUP_X1));
                                }
                            } else {
                                if (stack[stack.length - 4] instanceof AbstractInsnNode) {
                                    instructions.set(node, new InsnNode(DUP_X1));
                                } else {
                                    instructions.set(node, new InsnNode(DUP_X2));
                                }
                            }
                            stackSize += 1;
                        } else {
                            if (stack[stack.length - 3] instanceof AbstractInsnNode) {
                                if (stack[stack.length - 4] instanceof AbstractInsnNode) {
                                    instructions.set(node, new InsnNode(DUP2));
                                } else {
                                    instructions.set(node, new InsnNode(DUP2_X1));
                                }
                            } else {
                                if (stack[stack.length - 4] instanceof AbstractInsnNode) {
                                    instructions.set(node, new InsnNode(DUP2_X1));
                                }
                            }
                            stackSize += 2;
                        }
                        updateMaxStack(stackSize);
                        removeFrame = false;
                    }
                    break;
                case SWAP:
                    stack = frames.get(node).stack;
                    if (stack[stack.length - 1] instanceof AbstractInsnNode ||
                            stack[stack.length - 2] instanceof AbstractInsnNode) {
                        instructions.remove(node);
                    } else {
                        removeFrame = false;
                    }
                    break;
                case IF_ACMPEQ:
                    stack = frames.get(node).stack;
                    if (stack[stack.length - 1] instanceof AbstractInsnNode) {
                        if (stack[stack.length - 2] instanceof AbstractInsnNode) {
                            if (stack[stack.length - 1] == stack[stack.length - 2]) {
                                instructions.set(node, new JumpInsnNode(GOTO, ((JumpInsnNode) node).label));
                                removeFrame = false;
                            } else {
                                instructions.remove(node);
                            }
                        } else {
                            instructions.set(node, new InsnNode(POP));
                            stackSize -= 1;
                            removeFrame = false;
                        }
                    } else {
                        if (stack[stack.length - 2] instanceof AbstractInsnNode) {
                            instructions.set(node, new InsnNode(POP));
                            stackSize -= 1;
                        } else {
                            stackSize -= 2;
                        }
                        removeFrame = false;
                    }
                    break;
                case IF_ACMPNE:
                    stack = frames.get(node).stack;
                    if (stack[stack.length - 1] instanceof AbstractInsnNode) {
                        if (stack[stack.length - 2] instanceof AbstractInsnNode) {
                            if (stack[stack.length - 1] == stack[stack.length - 2]) {
                                instructions.remove(node);
                            } else {
                                instructions.set(node, new JumpInsnNode(GOTO, ((JumpInsnNode) node).label));
                                removeFrame = false;
                            }
                        } else {
                            instructions.insertBefore(node, new InsnNode(POP));
                            stackSize -= 1;
                            instructions.set(node, new JumpInsnNode(GOTO, ((JumpInsnNode) node).label));
                            removeFrame = false;
                        }
                    } else {
                        if (stack[stack.length - 2] instanceof AbstractInsnNode) {
                            instructions.insertBefore(node, new InsnNode(POP));
                            stackSize -= 1;
                            instructions.set(node, new JumpInsnNode(GOTO, ((JumpInsnNode) node).label));
                        } else {
                            stackSize -= 2;
                        }
                        removeFrame = false;
                    }
                    break;
                case IRETURN:
                case LRETURN:
                case FRETURN:
                case DRETURN:
                case ARETURN:
                case RETURN:
                    stackSize = 0;
                    removeFrame = false;
                    break;
                case GETSTATIC:
                    stackSize += getTypeSize(((FieldInsnNode) node).desc);
                    updateMaxStack(stackSize);
                    removeFrame = false;
                    break;
                case PUTSTATIC:
                    stackSize -= getTypeSize(((FieldInsnNode) node).desc);
                    removeFrame = false;
                    break;
                case GETFIELD:
                    stackSize += getTypeSize(((FieldInsnNode) node).desc) - 1;
                    updateMaxStack(stackSize);
                    removeFrame = false;
                    break;
                case PUTFIELD:
                    stackSize -= getTypeSize(((FieldInsnNode) node).desc) + 1;
                    removeFrame = false;
                    break;
                case INVOKEVIRTUAL:
                case INVOKEINTERFACE:
                    stackSize += getInvokeDelta(((MethodInsnNode) node).desc);
                    updateMaxStack(stackSize);
                    removeFrame = false;
                    break;
                case INVOKESPECIAL:
                    stackSize = handleInvokeSpecial((MethodInsnNode) node, stackSize);
                    removeFrame = false;
                    break;
                case INVOKESTATIC:
                    stackSize += getInvokeDelta(((MethodInsnNode) node).desc) + 1;
                    updateMaxStack(stackSize);
                    removeFrame = false;
                    break;
                case INVOKEDYNAMIC:
                    stackSize += getInvokeDelta(((InvokeDynamicInsnNode) node).desc) + 1;
                    updateMaxStack(stackSize);
                    removeFrame = false;
                    break;
                case NEW:
                    instructions.remove(node);
                    break;
                case ATHROW:
                    stackSize += 1 - stackSize;
                    updateMaxStack(stackSize);
                    removeFrame = false;
                    break;
                case MULTIANEWARRAY:
                    stackSize += 1 - ((MultiANewArrayInsnNode) node).dims;
                    updateMaxStack(stackSize);
                    removeFrame = false;
                    break;
                case IFNULL:
                    stack = frames.get(node).stack;
                    if (!(stack[stack.length - 1] instanceof AbstractInsnNode)) {
                        stackSize -= 1;
                        removeFrame = false;
                    }
                    break;
                default: // IFNONNULL:
                    stack = frames.get(node).stack;
                    if (stack[stack.length - 1] instanceof AbstractInsnNode) {
                        instructions.set(node, new JumpInsnNode(GOTO, ((JumpInsnNode) node).label));
                    } else {
                        stackSize -= 1;
                    }
                    removeFrame = false;
            }
        }

        accept(mv);
    }

    private int handleFrame(FrameNode node) {
        List<Object> local = node.local;
        Collection<Object> local1 = new ArrayList<>(local.size());
        for (Object value : local) {
            if (value instanceof LabelNode) {
                local1.add(TOP);
            } else {
                local1.add(value);
            }
        }

        int stackSize = 0;

        List<Object> stack = node.stack;
        Collection<Object> stack1 = new ArrayList<>(stack.size());
        for (Object value : stack) {
            if (!(value instanceof LabelNode)) {
                stack1.add(value);
                stackSize += isLong(value) ? 2 : 1;
            }
        }

        instructions
                .set(node, new FrameNode(node.type, local1.size(), local1.toArray(), stack1.size(), stack1.toArray()));

        updateMaxStack(stackSize);

        return stackSize;
    }

    private static int getTypeSize(String desc) {
        char c = desc.charAt(0);
        return c == 'J' || c == 'D' ? 2 : 1;
    }

    private static int getInvokeDelta(String desc) {
        int sizes = Type.getArgumentsAndReturnSizes(desc);
        return (sizes & 0x03) - (sizes >> 2);
    }

    private int handleInvokeSpecial(MethodInsnNode node, int stackSize) {
        int stackSize1 = stackSize;
        int sizes = Type.getArgumentsAndReturnSizes(node.desc);
        int argSize = sizes >> 2;

        if (node.name.charAt(0) == '<') {
            Frame frame = frames.get(node);
            Object[] locals = frame.locals;
            Object[] stack = frame.stack;
            AbstractInsnNode newNode = (AbstractInsnNode) stack[stack.length - argSize];

            boolean noLocalNews = true;
            for (Object value : locals) {
                if (value == newNode) {
                    noLocalNews = false;
                    break;
                }
            }

            int stackNewCount = 0;
            for (Object value : stack) {
                if (value == newNode) {
                    stackNewCount++;
                }
            }

            if (noLocalNews && stackNewCount == 2 && stack[stack.length - argSize - 1] == newNode) {
                if (argSize == 1) {
                    instructions.insertBefore(node, new TypeInsnNode(NEW, node.owner));
                    instructions.insertBefore(node, new InsnNode(DUP));
                    stackSize1 += 2;
                    updateMaxStack(stackSize1);
                } else if (argSize == 2 || argSize >= 4 && isLong(stack[stack.length - argSize + 2])) {
                    int varIndex = locals.length;

                    for (int i = stack.length - 1, k = stack.length - argSize + 2; i >= k; i--) {
                        Object value = stack[i];
                        if (value == INTEGER) {
                            instructions.insertBefore(node, new VarInsnNode(ISTORE, varIndex));
                            stackSize1 -= 1;
                            varIndex += 1;
                        } else if (value == FLOAT) {
                            instructions.insertBefore(node, new VarInsnNode(FSTORE, varIndex));
                            stackSize1 -= 1;
                            varIndex += 1;
                        } else if (value == DOUBLE) {
                            instructions.insertBefore(node, new VarInsnNode(DSTORE, varIndex));
                            stackSize1 -= 2;
                            varIndex += 2;
                        } else if (value == LONG) {
                            instructions.insertBefore(node, new VarInsnNode(LSTORE, varIndex));
                            stackSize1 -= 2;
                            varIndex += 2;
                        } else if (value == NULL) {
                            instructions.insertBefore(node, new InsnNode(POP));
                            stackSize1 -= 1;
                        } else if (value instanceof String) {
                            instructions.insertBefore(node, new VarInsnNode(ASTORE, varIndex));
                            stackSize1 -= 1;
                            varIndex += 1;
                        }
                    }

                    if (maxLocals < varIndex) {
                        maxLocals = varIndex;
                    }

                    instructions.insertBefore(node, new TypeInsnNode(NEW, node.owner));
                    instructions.insertBefore(node, new InsnNode(DUP));
                    instructions.insertBefore(node, new InsnNode(DUP2_X1));
                    instructions.insertBefore(node, new InsnNode(POP2));
                    stackSize1 += 2;
                    updateMaxStack(stackSize1 + 2);

                    for (int i = stack.length - argSize + 2, n = stack.length; i < n; i++) {
                        Object value = stack[i];
                        if (value == INTEGER) {
                            varIndex -= 1;
                            instructions.insertBefore(node, new VarInsnNode(ILOAD, varIndex));
                            stackSize1 += 1;
                        } else if (value == FLOAT) {
                            varIndex -= 1;
                            instructions.insertBefore(node, new VarInsnNode(FLOAD, varIndex));
                            stackSize1 += 1;
                        } else if (value == DOUBLE) {
                            varIndex -= 2;
                            instructions.insertBefore(node, new VarInsnNode(DLOAD, varIndex));
                            stackSize1 += 2;
                        } else if (value == LONG) {
                            varIndex -= 2;
                            instructions.insertBefore(node, new VarInsnNode(LLOAD, varIndex));
                            stackSize1 += 2;
                        } else if (value == NULL) {
                            instructions.insertBefore(node, new InsnNode(ACONST_NULL));
                            stackSize1 += 1;
                        } else if (value instanceof String) {
                            varIndex -= 1;
                            instructions.insertBefore(node, new VarInsnNode(ALOAD, varIndex));
                            stackSize1 += 1;
                        }
                    }

                    updateMaxStack(stackSize1);
                } else {
                    int varIndex = locals.length;

                    for (int i = stack.length - 1, k = stack.length - argSize + 3; i >= k; i--) {
                        Object value = stack[i];
                        if (value == INTEGER) {
                            instructions.insertBefore(node, new VarInsnNode(ISTORE, varIndex));
                            stackSize1 -= 1;
                            varIndex += 1;
                        } else if (value == FLOAT) {
                            instructions.insertBefore(node, new VarInsnNode(FSTORE, varIndex));
                            stackSize1 -= 1;
                            varIndex += 1;
                        } else if (value == DOUBLE) {
                            instructions.insertBefore(node, new VarInsnNode(DSTORE, varIndex));
                            stackSize1 -= 2;
                            varIndex += 2;
                        } else if (value == LONG) {
                            instructions.insertBefore(node, new VarInsnNode(LSTORE, varIndex));
                            stackSize1 -= 2;
                            varIndex += 2;
                        } else if (value == NULL) {
                            instructions.insertBefore(node, new InsnNode(POP));
                            stackSize1 -= 1;
                        } else if (value instanceof String) {
                            instructions.insertBefore(node, new VarInsnNode(ASTORE, varIndex));
                            stackSize1 -= 1;
                            varIndex += 1;
                        }
                    }

                    if (maxLocals < varIndex) {
                        maxLocals = varIndex;
                    }

                    instructions.insertBefore(node, new TypeInsnNode(NEW, node.owner));
                    instructions.insertBefore(node, new InsnNode(DUP));
                    instructions.insertBefore(node, new InsnNode(DUP2_X2));
                    instructions.insertBefore(node, new InsnNode(POP2));
                    stackSize1 += 2;
                    updateMaxStack(stackSize1 + 2);

                    for (int i = stack.length - argSize + 3, n = stack.length; i < n; i++) {
                        Object value = stack[i];
                        if (value == INTEGER) {
                            varIndex -= 1;
                            instructions.insertBefore(node, new VarInsnNode(ILOAD, varIndex));
                            stackSize1 += 1;
                        } else if (value == FLOAT) {
                            varIndex -= 1;
                            instructions.insertBefore(node, new VarInsnNode(FLOAD, varIndex));
                            stackSize1 += 1;
                        } else if (value == DOUBLE) {
                            varIndex -= 2;
                            instructions.insertBefore(node, new VarInsnNode(DLOAD, varIndex));
                            stackSize1 += 2;
                        } else if (value == LONG) {
                            varIndex -= 2;
                            instructions.insertBefore(node, new VarInsnNode(LLOAD, varIndex));
                            stackSize1 += 2;
                        } else if (value == NULL) {
                            instructions.insertBefore(node, new InsnNode(ACONST_NULL));
                            stackSize1 += 1;
                        } else if (value instanceof String) {
                            varIndex -= 1;
                            instructions.insertBefore(node, new VarInsnNode(ALOAD, varIndex));
                            stackSize1 += 1;
                        }
                    }

                    updateMaxStack(stackSize1);
                }
            } else {
                int k = 0;
                for (Object value : stack) {
                    if (value == newNode) {
                        break;
                    }
                    k++;
                }

                int newVarIndex = locals.length;
                int varIndex = newVarIndex + 1;

                for (int i = stack.length - 1; i >= k; i--) {
                    Object value = stack[i];
                    if (value == INTEGER) {
                        instructions.insertBefore(node, new VarInsnNode(ISTORE, varIndex));
                        stackSize1 -= 1;
                        varIndex += 1;
                    } else if (value == FLOAT) {
                        instructions.insertBefore(node, new VarInsnNode(FSTORE, varIndex));
                        stackSize1 -= 1;
                        varIndex += 1;
                    } else if (value == DOUBLE) {
                        instructions.insertBefore(node, new VarInsnNode(DSTORE, varIndex));
                        stackSize1 -= 2;
                        varIndex += 2;
                    } else if (value == LONG) {
                        instructions.insertBefore(node, new VarInsnNode(LSTORE, varIndex));
                        stackSize1 -= 2;
                        varIndex += 2;
                    } else if (value == NULL) {
                        instructions.insertBefore(node, new InsnNode(POP));
                        stackSize1 -= 1;
                    } else if (value instanceof String) {
                        instructions.insertBefore(node, new VarInsnNode(ASTORE, varIndex));
                        stackSize1 -= 1;
                        varIndex += 1;
                    }
                }

                if (maxLocals < varIndex) {
                    maxLocals = varIndex;
                }

                instructions.insertBefore(node, new TypeInsnNode(NEW, node.owner));
                instructions.insertBefore(node, new VarInsnNode(ASTORE, newVarIndex));

                for (int i = 0; i < newVarIndex; i++) {
                    if (locals[i] == newNode) {
                        instructions.insertBefore(node, new VarInsnNode(ALOAD, newVarIndex));
                        instructions.insertBefore(node, new VarInsnNode(ASTORE, i));
                    }
                }

                updateMaxStack(stackSize1 + 1);

                for (int i = k, n = stack.length; i < n; i++) {
                    Object value = stack[i];
                    if (value == INTEGER) {
                        varIndex -= 1;
                        instructions.insertBefore(node, new VarInsnNode(ILOAD, varIndex));
                        stackSize1 += 1;
                    } else if (value == FLOAT) {
                        varIndex -= 1;
                        instructions.insertBefore(node, new VarInsnNode(FLOAD, varIndex));
                        stackSize1 += 1;
                    } else if (value == DOUBLE) {
                        varIndex -= 2;
                        instructions.insertBefore(node, new VarInsnNode(DLOAD, varIndex));
                        stackSize1 += 2;
                    } else if (value == LONG) {
                        varIndex -= 2;
                        instructions.insertBefore(node, new VarInsnNode(LLOAD, varIndex));
                        stackSize1 += 2;
                    } else if (value == NULL) {
                        instructions.insertBefore(node, new InsnNode(ACONST_NULL));
                        stackSize1 += 1;
                    } else if (value instanceof String) {
                        varIndex -= 1;
                        instructions.insertBefore(node, new VarInsnNode(ALOAD, varIndex));
                        stackSize1 += 1;
                    } else if (value == newNode) {
                        instructions.insertBefore(node, new VarInsnNode(ALOAD, newVarIndex));
                        stackSize1 += 1;
                    }
                }

                updateMaxStack(stackSize1);
            }
        }

        stackSize1 += (sizes & 0x03) - argSize;
        updateMaxStack(stackSize1);

        return stackSize1;
    }

    private static boolean isLong(Object value) {
        return value == LONG || value == DOUBLE;
    }

    private void updateMaxStack(int stackSize) {
        if (maxStack < stackSize) {
            maxStack = stackSize;
        }
    }
}
