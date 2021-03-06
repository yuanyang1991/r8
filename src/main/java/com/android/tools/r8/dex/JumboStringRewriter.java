// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex;

import static com.android.tools.r8.graph.DexCode.TryHandler.NO_HANDLER;
import static com.android.tools.r8.graph.DexDebugEventBuilder.addDefaultEventWithAdvancePcIfNecessary;

import com.android.tools.r8.code.ConstString;
import com.android.tools.r8.code.ConstStringJumbo;
import com.android.tools.r8.code.Format21t;
import com.android.tools.r8.code.Format22t;
import com.android.tools.r8.code.Format31t;
import com.android.tools.r8.code.Goto;
import com.android.tools.r8.code.Goto16;
import com.android.tools.r8.code.Goto32;
import com.android.tools.r8.code.IfEq;
import com.android.tools.r8.code.IfEqz;
import com.android.tools.r8.code.IfGe;
import com.android.tools.r8.code.IfGez;
import com.android.tools.r8.code.IfGt;
import com.android.tools.r8.code.IfGtz;
import com.android.tools.r8.code.IfLe;
import com.android.tools.r8.code.IfLez;
import com.android.tools.r8.code.IfLt;
import com.android.tools.r8.code.IfLtz;
import com.android.tools.r8.code.IfNe;
import com.android.tools.r8.code.IfNez;
import com.android.tools.r8.code.Instruction;
import com.android.tools.r8.code.Nop;
import com.android.tools.r8.code.SwitchPayload;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexCode.Try;
import com.android.tools.r8.graph.DexCode.TryHandler;
import com.android.tools.r8.graph.DexCode.TryHandler.TypeAddrPair;
import com.android.tools.r8.graph.DexDebugEvent;
import com.android.tools.r8.graph.DexDebugEvent.AdvancePC;
import com.android.tools.r8.graph.DexDebugEvent.Default;
import com.android.tools.r8.graph.DexDebugInfo;
import com.android.tools.r8.graph.DexDebugInfo.EventBasedDebugInfo;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexString;
import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;

public class JumboStringRewriter {

  private static class TryTargets {
    private Instruction start;
    private Instruction end;
    private final boolean endsAfterLastInstruction;

    TryTargets(Instruction start, Instruction end, boolean endsAfterLastInstruction) {
      assert start != null;
      assert end != null;
      this.start = start;
      this.end = end;
      this.endsAfterLastInstruction = endsAfterLastInstruction;
    }

    void replaceTarget(Instruction target, Instruction newTarget) {
      if (start == target) {
        start = newTarget;
      }
      if (end == target) {
        end = newTarget;
      }
    }

    int getStartOffset() {
      return start.getOffset();
    }

    int getStartToEndDelta() {
      if (endsAfterLastInstruction) {
        return end.getOffset() + end.getSize() - start.getOffset();
      }
      return end.getOffset() - start.getOffset();
    }
  }

  private final DexEncodedMethod method;
  private final DexString firstJumboString;
  private final DexItemFactory factory;
  private final Map<Instruction, List<Instruction>> instructionTargets = new IdentityHashMap<>();
  private EventBasedDebugInfo debugEventBasedInfo = null;
  private final Int2ReferenceMap<Instruction> debugEventTargets = new Int2ReferenceOpenHashMap<>();
  private final Map<Instruction, Instruction> payloadToSwitch = new IdentityHashMap<>();
  private final Map<Try, TryTargets> tryTargets = new IdentityHashMap<>();
  private final Int2ReferenceMap<Instruction> tryRangeStartAndEndTargets
      = new Int2ReferenceOpenHashMap<>();
  private final Map<TryHandler, List<Instruction>> handlerTargets = new IdentityHashMap<>();

  public JumboStringRewriter(
      DexEncodedMethod method, DexString firstJumboString, DexItemFactory factory) {
    this.method = method;
    this.firstJumboString = firstJumboString;
    this.factory = factory;
  }

  public DexCode rewrite() {
    // Build maps from everything in the code that uses offsets or direct addresses to reference
    // instructions to the actual instruction referenced.
    recordTargets();
    // Expand the code by rewriting jumbo strings and branching instructions.
    List<Instruction> newInstructions = expandCode();
    // Commit to the new instruction offsets and update instructions, try-catch structures
    // and debug info with the new offsets.
    rewriteInstructionOffsets(newInstructions);
    Try[] newTries = rewriteTryOffsets();
    TryHandler[] newHandlers = rewriteHandlerOffsets();
    DexDebugInfo newDebugInfo = rewriteDebugInfoOffsets();
    // Set the new code on the method.
    DexCode oldCode = method.getCode().asDexCode();
    DexCode newCode =
        new DexCode(
            oldCode.registerSize,
            oldCode.incomingRegisterSize,
            oldCode.outgoingRegisterSize,
            newInstructions.toArray(Instruction.EMPTY_ARRAY),
            newTries,
            newHandlers,
            newDebugInfo);
    // As we have rewritten the code, we now know that its highest string index that is not
    // a jumbo-string is firstJumboString (actually the previous string, but we do not have that).
    newCode.highestSortingString = firstJumboString;
    return newCode;
  }

  private void rewriteInstructionOffsets(List<Instruction> instructions) {
    for (Instruction instruction : instructions) {
      if (instruction instanceof Format22t) {  // IfEq, IfGe, IfGt, IfLe, IfLt, IfNe
        Format22t condition = (Format22t) instruction;
        int offset = instructionTargets.get(condition).get(0).getOffset() - instruction.getOffset();
        assert Short.MIN_VALUE <= offset && offset <= Short.MAX_VALUE;
        condition.CCCC = (short) offset;
      } else if (instruction instanceof Format21t) {  // IfEqz, IfGez, IfGtz, IfLez, IfLtz, IfNez
        Format21t condition = (Format21t) instruction;
        int offset = instructionTargets.get(condition).get(0).getOffset() - instruction.getOffset();
        assert Short.MIN_VALUE <= offset && offset <= Short.MAX_VALUE;
        condition.BBBB = (short) offset;
      } else if (instruction instanceof Goto) {
        Goto jump = (Goto) instruction;
        int offset = instructionTargets.get(jump).get(0).getOffset() - instruction.getOffset();
        assert Byte.MIN_VALUE <= offset && offset <= Byte.MAX_VALUE;
        jump.AA = (byte) offset;
      } else if (instruction instanceof Goto16) {
        Goto16 jump = (Goto16) instruction;
        int offset = instructionTargets.get(jump).get(0).getOffset() - instruction.getOffset();
        assert Short.MIN_VALUE <= offset && offset <= Short.MAX_VALUE;
        jump.AAAA = (short) offset;
      } else if (instruction instanceof Goto32) {
        Goto32 jump = (Goto32) instruction;
        int offset = instructionTargets.get(jump).get(0).getOffset() - instruction.getOffset();
        jump.AAAAAAAA = offset;
      } else if (instruction.hasPayload()) {  // FillArrayData, SparseSwitch, PackedSwitch
        Format31t payloadUser = (Format31t) instruction;
        int offset =
            instructionTargets.get(payloadUser).get(0).getOffset() - instruction.getOffset();
        payloadUser.setPayloadOffset(offset);
      } else if (instruction instanceof SwitchPayload) {
        SwitchPayload payload = (SwitchPayload) instruction;
        Instruction switchInstruction = payloadToSwitch.get(payload);
        List<Instruction> switchTargets = instructionTargets.get(payload);
        int[] targets = payload.switchTargetOffsets();
        for (int i = 0; i < switchTargets.size(); i++) {
          Instruction target = switchTargets.get(i);
          targets[i] = target.getOffset() - switchInstruction.getOffset();
        }
      }
    }
  }

  private Try[] rewriteTryOffsets() {
    DexCode code = method.getCode().asDexCode();
    Try[] result = new Try[code.tries.length];
    for (int i = 0; i < code.tries.length; i++) {
      Try theTry = code.tries[i];
      TryTargets targets = tryTargets.get(theTry);
      result[i] = new Try(targets.getStartOffset(), targets.getStartToEndDelta(), -1);
      result[i].handlerIndex = theTry.handlerIndex;
    }
    return result;
  }

  private TryHandler[] rewriteHandlerOffsets() {
    DexCode code = method.getCode().asDexCode();
    TryHandler[] result = new TryHandler[code.handlers.length];
    for (int i = 0; i < code.handlers.length; i++) {
      TryHandler handler = code.handlers[i];
      List<Instruction> targets = handlerTargets.get(handler);
      Iterator<Instruction> it = targets.iterator();
      int catchAllAddr = NO_HANDLER;
      if (handler.catchAllAddr != NO_HANDLER) {
        catchAllAddr = it.next().getOffset();
      }
      TypeAddrPair[] newPairs = new TypeAddrPair[handler.pairs.length];
      for (int j = 0; j < handler.pairs.length; j++) {
        TypeAddrPair pair = handler.pairs[j];
        newPairs[j] = new TypeAddrPair(pair.getType(), it.next().getOffset());
      }
      result[i] = new TryHandler(newPairs, catchAllAddr);
    }
    return result;
  }

  private DexDebugInfo rewriteDebugInfoOffsets() {
    DexCode code = method.getCode().asDexCode();
    if (!debugEventTargets.isEmpty()) {
      assert debugEventBasedInfo != null;
      int lastOriginalOffset = 0;
      int lastNewOffset = 0;
      List<DexDebugEvent> events = new ArrayList<>();
      for (DexDebugEvent event : debugEventBasedInfo.events) {
        if (event instanceof AdvancePC) {
          AdvancePC advance = (AdvancePC) event;
          lastOriginalOffset += advance.delta;
          Instruction target = debugEventTargets.get(lastOriginalOffset);
          int pcDelta = target.getOffset() - lastNewOffset;
          events.add(factory.createAdvancePC(pcDelta));
          lastNewOffset = target.getOffset();
        } else if (event instanceof Default) {
          Default defaultEvent = (Default) event;
          lastOriginalOffset += defaultEvent.getPCDelta();
          Instruction target = debugEventTargets.get(lastOriginalOffset);
          int lineDelta = defaultEvent.getLineDelta();
          int pcDelta = target.getOffset() - lastNewOffset;
          addDefaultEventWithAdvancePcIfNecessary(lineDelta, pcDelta, events, factory);
          lastNewOffset = target.getOffset();
        } else {
          events.add(event);
        }
      }
      return new EventBasedDebugInfo(
          debugEventBasedInfo.startLine,
          debugEventBasedInfo.parameters,
          events.toArray(DexDebugEvent.EMPTY_ARRAY));
    }
    return code.getDebugInfo();
  }

  private List<Instruction> expandCode() {
    LinkedList<Instruction> instructions = new LinkedList<>();
    Collections.addAll(instructions, method.getCode().asDexCode().instructions);
    int offsetDelta;
    do {
      ListIterator<Instruction> it = instructions.listIterator();
      offsetDelta = 0;
      while (it.hasNext()) {
        Instruction instruction = it.next();
        int orignalOffset = instruction.getOffset();
        instruction.setOffset(orignalOffset + offsetDelta);
        if (instruction instanceof ConstString) {
          ConstString string = (ConstString) instruction;
          if (string.getString().compareTo(firstJumboString) >= 0) {
            ConstStringJumbo jumboString = new ConstStringJumbo(string.AA, string.getString());
            jumboString.setOffset(string.getOffset());
            offsetDelta++;
            it.set(jumboString);
            replaceTarget(instruction, jumboString);
          }
        } else if (instruction instanceof Format22t) {  // IfEq, IfGe, IfGt, IfLe, IfLt, IfNe
          Format22t condition = (Format22t) instruction;
          int offset =
              instructionTargets.get(condition).get(0).getOffset() - instruction.getOffset();
          if (Short.MIN_VALUE > offset || offset > Short.MAX_VALUE) {
            Format22t newCondition = null;
            switch (condition.getType().inverted()) {
              case EQ:
                newCondition = new IfEq(condition.A, condition.B, 0);
                break;
              case GE:
                newCondition = new IfGe(condition.A, condition.B, 0);
                break;
              case GT:
                newCondition = new IfGt(condition.A, condition.B, 0);
                break;
              case LE:
                newCondition = new IfLe(condition.A, condition.B, 0);
                break;
              case LT:
                newCondition = new IfLt(condition.A, condition.B, 0);
                break;
              case NE:
                newCondition = new IfNe(condition.A, condition.B, 0);
                break;
            }
            offsetDelta = rewriteIfToIfAndGoto(offsetDelta, it, condition, newCondition);
          }
        } else if (instruction instanceof Format21t) {  // IfEqz, IfGez, IfGtz, IfLez, IfLtz, IfNez
          Format21t condition = (Format21t) instruction;
          int offset =
              instructionTargets.get(condition).get(0).getOffset() - instruction.getOffset();
          if (Short.MIN_VALUE > offset || offset > Short.MAX_VALUE) {
            Format21t newCondition = null;
            switch (condition.getType().inverted()) {
              case EQ:
                newCondition = new IfEqz(condition.AA, 0);
                break;
              case GE:
                newCondition = new IfGez(condition.AA, 0);
                break;
              case GT:
                newCondition = new IfGtz(condition.AA, 0);
                break;
              case LE:
                newCondition = new IfLez(condition.AA, 0);
                break;
              case LT:
                newCondition = new IfLtz(condition.AA, 0);
                break;
              case NE:
                newCondition = new IfNez(condition.AA, 0);
                break;
            }
            offsetDelta = rewriteIfToIfAndGoto(offsetDelta, it, condition, newCondition);
          }
        } else if (instruction instanceof Goto) {
          Goto jump = (Goto) instruction;
          int offset =
              instructionTargets.get(jump).get(0).getOffset() - instruction.getOffset();
          if (Byte.MIN_VALUE > offset || offset > Byte.MAX_VALUE) {
            Instruction newJump;
            if (Short.MIN_VALUE > offset || offset > Short.MAX_VALUE) {
              newJump = new Goto32(offset);
            } else {
              newJump = new Goto16(offset);
            }
            newJump.setOffset(jump.getOffset());
            it.set(newJump);
            offsetDelta += (newJump.getSize() - jump.getSize());
            replaceTarget(jump, newJump);
            List<Instruction> targets = instructionTargets.remove(jump);
            instructionTargets.put(newJump, targets);
          }
        } else if (instruction instanceof Goto16) {
          Goto16 jump = (Goto16) instruction;
          int offset =
              instructionTargets.get(jump).get(0).getOffset() - instruction.getOffset();
          if (Short.MIN_VALUE > offset || offset > Short.MAX_VALUE) {
            Instruction newJump = new Goto32(offset);
            newJump.setOffset(jump.getOffset());
            it.set(newJump);
            offsetDelta += (newJump.getSize() - jump.getSize());
            replaceTarget(jump, newJump);
            List<Instruction> targets = instructionTargets.remove(jump);
            instructionTargets.put(newJump, targets);
          }
        } else if (instruction instanceof Goto32) {
          // Instruction big enough for any offset.
        } else if (instruction.hasPayload()) {  // FillArrayData, SparseSwitch, PackedSwitch
          // Instruction big enough for any offset.
        } else if (instruction.isPayload()) {
          // Payload instructions must be 4 byte aligned (instructions are 2 bytes).
          if (instruction.getOffset() % 2 != 0) {
            it.previous();
            // Check if the previous instruction was a simple nop. If that is the case, remove it
            // to make the alignment instead of adding another one. Only allow removal if this
            // instruction is not targeted by anything. See b/78072750.
            Instruction instructionBeforePayload = it.hasPrevious() ? it.previous() : null;
            if (instructionBeforePayload != null
                && instructionBeforePayload.isSimpleNop()
                && debugEventTargets.get(orignalOffset) == null
                && tryRangeStartAndEndTargets.get(orignalOffset) == null) {
              it.remove();
              offsetDelta--;
            } else {
              if (instructionBeforePayload != null) {
                it.next();
              }
              Nop nop = new Nop();
              nop.setOffset(instruction.getOffset());
              it.add(nop);
              offsetDelta++;
            }
            instruction.setOffset(orignalOffset + offsetDelta);
            it.next();
          }
          // Instruction big enough for any offset.
        }
      }
    } while (offsetDelta > 0);
    return instructions;
  }

  private int rewriteIfToIfAndGoto(
      int offsetDelta,
      ListIterator<Instruction> it,
      Instruction condition,
      Instruction newCondition) {
    int jumpOffset = condition.getOffset() + condition.getSize();
    Goto32 jump = new Goto32(0);
    jump.setOffset(jumpOffset);
    newCondition.setOffset(condition.getOffset());
    it.set(newCondition);
    replaceTarget(condition, newCondition);
    it.add(jump);
    offsetDelta += jump.getSize();
    instructionTargets.put(jump, instructionTargets.remove(condition));
    Instruction fallthroughInstruction = it.next();
    instructionTargets.put(newCondition, Lists.newArrayList(fallthroughInstruction));
    it.previous();
    return offsetDelta;
  }

  private void replaceTarget(Instruction target, Instruction newTarget) {
    for (List<Instruction> instructions : instructionTargets.values()) {
      instructions.replaceAll((i) -> i == target ? newTarget : i);
    }
    for (Int2ReferenceMap.Entry<Instruction> entry : debugEventTargets.int2ReferenceEntrySet()) {
      if (entry.getValue() == target) {
        entry.setValue(newTarget);
      }
    }
    for (Entry<Try, TryTargets> entry : tryTargets.entrySet()) {
      entry.getValue().replaceTarget(target, newTarget);
    }
    for (List<Instruction> instructions : handlerTargets.values()) {
      instructions.replaceAll((i) -> i == target ? newTarget : i);
    }
  }

  private void recordInstructionTargets(Int2ReferenceMap<Instruction> offsetToInstruction) {
    Instruction[] instructions = method.getCode().asDexCode().instructions;
    for (Instruction instruction : instructions) {
      if (instruction instanceof Format22t) {  // IfEq, IfGe, IfGt, IfLe, IfLt, IfNe
        Format22t condition = (Format22t) instruction;
        Instruction target = offsetToInstruction.get(condition.getOffset() + condition.CCCC);
        assert target != null;
        instructionTargets.put(instruction, Lists.newArrayList(target));
      } else if (instruction instanceof Format21t) {  // IfEqz, IfGez, IfGtz, IfLez, IfLtz, IfNez
        Format21t condition = (Format21t) instruction;
        Instruction target = offsetToInstruction.get(condition.getOffset() + condition.BBBB);
        assert target != null;
        instructionTargets.put(instruction, Lists.newArrayList(target));
      } else if (instruction instanceof Goto) {
        Goto jump = (Goto) instruction;
        Instruction target = offsetToInstruction.get(jump.getOffset() + jump.AA);
        assert target != null;
        instructionTargets.put(instruction, Lists.newArrayList(target));
      } else if (instruction instanceof Goto16) {
        Goto16 jump = (Goto16) instruction;
        Instruction target = offsetToInstruction.get(jump.getOffset() + jump.AAAA);
        assert target != null;
        instructionTargets.put(instruction, Lists.newArrayList(target));
      } else if (instruction instanceof Goto32) {
        Goto32 jump = (Goto32) instruction;
        Instruction target = offsetToInstruction.get(jump.getOffset() + jump.AAAAAAAA);
        assert target != null;
        instructionTargets.put(instruction, Lists.newArrayList(target));
      } else if (instruction.hasPayload()) {  // FillArrayData, SparseSwitch, PackedSwitch
        Format31t offsetInstruction = (Format31t) instruction;
        Instruction target = offsetToInstruction.get(
            offsetInstruction.getOffset() + offsetInstruction.getPayloadOffset());
        assert target != null;
        instructionTargets.put(instruction, Lists.newArrayList(target));
      } else if (instruction instanceof SwitchPayload) {
        SwitchPayload payload = (SwitchPayload) instruction;
        int[] targetOffsets = payload.switchTargetOffsets();
        int switchOffset = payloadToSwitch.get(instruction).getOffset();
        List<Instruction> targets = new ArrayList<>();
        for (int i = 0; i < targetOffsets.length; i++) {
          Instruction target = offsetToInstruction.get(switchOffset + targetOffsets[i]);
          assert target != null;
          targets.add(target);
        }
        instructionTargets.put(instruction, targets);
      }
    }
  }

  private void recordDebugEventTargets(Int2ReferenceMap<Instruction> offsetToInstruction) {
    // TODO(b/213411850): Merging pc based D8 builds will map out of PC for any jumbo processed
    //  method. Instead we should rather retain the PC encoding by bumping the max-pc and recording
    //  the line number translation. We actually need to do so to support merging with native PC
    //  support as in that case we can't reflect the change in the line table, only in the mapping.
    EventBasedDebugInfo eventBasedInfo =
        DexDebugInfo.convertToEventBased(method.getCode().asDexCode(), factory);
    if (eventBasedInfo == null) {
      return;
    }
    debugEventBasedInfo = eventBasedInfo;
    int address = 0;
    for (DexDebugEvent event : eventBasedInfo.events) {
      if (event instanceof AdvancePC) {
        AdvancePC advance = (AdvancePC) event;
        address += advance.delta;
        Instruction target = offsetToInstruction.get(address);
        assert target != null;
        debugEventTargets.put(address, target);
      } else if (event instanceof Default) {
        Default defaultEvent = (Default) event;
        address += defaultEvent.getPCDelta();
        Instruction target = offsetToInstruction.get(address);
        assert target != null;
        debugEventTargets.put(address, target);
      }
    }
  }

  private void recordTryAndHandlerTargets(
      Int2ReferenceMap<Instruction> offsetToInstruction,
      Instruction lastInstruction) {
    DexCode code = method.getCode().asDexCode();
    for (Try theTry : code.tries) {
      Instruction start = offsetToInstruction.get(theTry.startAddress);
      Instruction end = null;
      int endAddress = theTry.startAddress + theTry.instructionCount;
      TryTargets targets;
      if (endAddress > lastInstruction.getOffset()) {
        end = lastInstruction;
        targets = new TryTargets(start, lastInstruction, true);
      } else {
        end = offsetToInstruction.get(endAddress);
        targets = new TryTargets(start, end, false);
      }
      assert theTry.startAddress == targets.getStartOffset();
      assert theTry.instructionCount == targets.getStartToEndDelta();
      tryTargets.put(theTry, targets);
      tryRangeStartAndEndTargets.put(start.getOffset(), start);
      tryRangeStartAndEndTargets.put(end.getOffset(), end);
    }
    for (TryHandler handler : code.handlers) {
      List<Instruction> targets = new ArrayList<>();
      if (handler.catchAllAddr != NO_HANDLER) {
        Instruction target = offsetToInstruction.get(handler.catchAllAddr);
        assert target != null;
        targets.add(target);
      }
      for (TypeAddrPair pair : handler.pairs) {
        Instruction target = offsetToInstruction.get(pair.addr);
        assert target != null;
        targets.add(target);
      }
      handlerTargets.put(handler, targets);
    }
  }

  private void recordTargets() {
    Int2ReferenceMap<Instruction> offsetToInstruction = new Int2ReferenceOpenHashMap<>();
    Instruction[] instructions = method.getCode().asDexCode().instructions;
    boolean containsPayloads = false;
    for (Instruction instruction : instructions) {
      offsetToInstruction.put(instruction.getOffset(), instruction);
      if (instruction.hasPayload()) {  // FillArrayData, SparseSwitch, PackedSwitch
        containsPayloads = true;
      }
    }
    if (containsPayloads) {
      for (Instruction instruction : instructions) {
        if (instruction.hasPayload()) {  // FillArrayData, SparseSwitch, PackedSwitch
          Instruction payload =
              offsetToInstruction.get(instruction.getOffset() + instruction.getPayloadOffset());
          assert payload != null;
          payloadToSwitch.put(payload, instruction);
        }
      }
    }
    recordInstructionTargets(offsetToInstruction);
    recordDebugEventTargets(offsetToInstruction);
    Instruction lastInstruction = instructions[instructions.length - 1];
    recordTryAndHandlerTargets(offsetToInstruction, lastInstruction);
  }
}
