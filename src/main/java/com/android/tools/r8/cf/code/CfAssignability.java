// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.code;

import com.android.tools.r8.cf.code.CfFrame.FrameType;
import com.android.tools.r8.cf.code.frame.SingleFrameType;
import com.android.tools.r8.cf.code.frame.WideFrameType;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.utils.MapUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectSortedMap;
import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;

public class CfAssignability {

  public static boolean isFrameTypeAssignable(
      FrameType source, FrameType target, AppView<?> appView) {
    if (source.isSingle() != target.isSingle()) {
      return false;
    }
    return source.isSingle()
        ? isFrameTypeAssignable(source.asSingle(), target.asSingle(), appView)
        : isFrameTypeAssignable(source.asWide(), target.asWide(), appView);
  }

  // Based on https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.10.1.2.
  public static boolean isFrameTypeAssignable(
      SingleFrameType source, SingleFrameType target, AppView<?> appView) {
    if (source == target || target.isOneWord()) {
      return true;
    }
    if (source.isOneWord()) {
      return false;
    }
    if (source.isUninitializedNew() && target.isUninitializedNew()) {
      // TODO(b/168190134): Allow for picking the offset from the target if not set.
      DexType uninitializedNewTypeSource = source.getUninitializedNewType();
      DexType uninitializedNewTypeTarget = target.getUninitializedNewType();
      return uninitializedNewTypeSource == null
          || uninitializedNewTypeTarget == null
          || uninitializedNewTypeSource == uninitializedNewTypeTarget;
    }
    // TODO(b/168190267): Clean-up the lattice.
    DexItemFactory factory = appView.dexItemFactory();
    if (target.isInitialized()) {
      if (source.isInitialized()) {
        // Both are instantiated types and we resort to primitive type/java type hierarchy checking.
        return isAssignable(
            source.getInitializedType(factory), target.getInitializedType(factory), appView);
      }
      return target.asSingleInitializedType().getInitializedType() == factory.objectType;
    }
    return false;
  }

  public static boolean isFrameTypeAssignable(
      WideFrameType source, WideFrameType target, AppView<?> appView) {
    assert !source.isTwoWord();
    return source.lessThanOrEqualTo(target);
  }

  // Rules found at https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.10.1.2
  public static boolean isAssignable(DexType source, DexType target, AppView<?> appView) {
    DexItemFactory factory = appView.dexItemFactory();
    source = byteCharShortOrBooleanToInt(source, factory);
    target = byteCharShortOrBooleanToInt(target, factory);
    if (source == target) {
      return true;
    }
    if (source.isPrimitiveType() || target.isPrimitiveType()) {
      return false;
    }
    // Both are now references - everything is assignable to object.
    if (target == factory.objectType) {
      return true;
    }
    // isAssignable(null, class(_, _)).
    // isAssignable(null, arrayOf(_)).
    if (source == DexItemFactory.nullValueType) {
      return true;
    }
    if (target.isArrayType() != target.isArrayType()) {
      return false;
    }
    if (target.isArrayType()) {
      return isAssignable(
          target.toArrayElementType(factory), target.toArrayElementType(factory), appView);
    }
    // TODO(b/166570659): Do a sub-type check that allows for missing classes in hierarchy.
    return MemberType.fromDexType(source) == MemberType.fromDexType(target);
  }

  public static boolean isAssignable(DexType source, ValueType target, AppView<?> appView) {
    return isAssignable(source, target.toDexType(appView.dexItemFactory()), appView);
  }

  private static DexType byteCharShortOrBooleanToInt(DexType type, DexItemFactory factory) {
    // byte, char, short and boolean has verification type int.
    return hasIntVerificationType(type) ? factory.intType : type;
  }

  public static boolean hasIntVerificationType(DexType type) {
    return type.isBooleanType()
        || type.isByteType()
        || type.isCharType()
        || type.isIntType()
        || type.isShortType();
  }

  public static AssignabilityResult isFrameAssignable(
      CfFrame source, CfFrame target, AppView<?> appView) {
    AssignabilityResult result =
        isLocalsAssignable(source.getLocals(), target.getLocals(), appView);
    return result.isSuccessful()
        ? isStackAssignable(source.getStack(), target.getStack(), appView)
        : result;
  }

  public static AssignabilityResult isLocalsAssignable(
      Int2ObjectSortedMap<FrameType> sourceLocals,
      Int2ObjectSortedMap<FrameType> targetLocals,
      AppView<?> appView) {
    // TODO(b/229826687): The tail of locals could have top(s) at destination but still be valid.
    int localsLastKey = sourceLocals.isEmpty() ? -1 : sourceLocals.lastIntKey();
    int otherLocalsLastKey = targetLocals.isEmpty() ? -1 : targetLocals.lastIntKey();
    if (localsLastKey < otherLocalsLastKey) {
      return new FailedAssignabilityResult(
          "Source locals "
              + MapUtils.toString(sourceLocals)
              + " have different local indices than "
              + MapUtils.toString(targetLocals));
    }
    for (int i = 0; i < otherLocalsLastKey; i++) {
      FrameType sourceType =
          sourceLocals.containsKey(i) ? sourceLocals.get(i) : FrameType.oneWord();
      FrameType destinationType =
          targetLocals.containsKey(i) ? targetLocals.get(i) : FrameType.oneWord();
      if (sourceType.isWide() && destinationType.isOneWord()) {
        destinationType = FrameType.twoWord();
      }
      if (!isFrameTypeAssignable(sourceType, destinationType, appView)) {
        return new FailedAssignabilityResult(
            "Could not assign '"
                + MapUtils.toString(sourceLocals)
                + "' to '"
                + MapUtils.toString(targetLocals)
                + "'. The local at index "
                + i
                + " with '"
                + sourceType
                + "' not being assignable to '"
                + destinationType
                + "'");
      }
    }
    return new SuccessfulAssignabilityResult();
  }

  public static AssignabilityResult isStackAssignable(
      Deque<FrameType> sourceStack, Deque<FrameType> targetStack, AppView<?> appView) {
    if (sourceStack.size() != targetStack.size()) {
      return new FailedAssignabilityResult(
          "Source stack "
              + Arrays.toString(sourceStack.toArray())
              + " and destination stack "
              + Arrays.toString(targetStack.toArray())
              + " is not the same size");
    }
    Iterator<FrameType> otherIterator = targetStack.iterator();
    int stackIndex = 0;
    for (FrameType sourceType : sourceStack) {
      FrameType destinationType = otherIterator.next();
      // TODO(b/231260627): By strengthening the stack to Deque<SpecificFrameType> the following
      //  asserts would be trivial as a result of type checking.
      assert sourceType.isSpecific();
      assert destinationType.isSpecific();
      if (!isFrameTypeAssignable(sourceType, destinationType, appView)) {
        return new FailedAssignabilityResult(
            "Could not assign '"
                + Arrays.toString(sourceStack.toArray())
                + "' to '"
                + Arrays.toString(targetStack.toArray())
                + "'. The stack value at index "
                + stackIndex
                + " (from top) with '"
                + sourceType
                + "' not being assignable to '"
                + destinationType
                + "'");
      }
      stackIndex++;
    }
    return new SuccessfulAssignabilityResult();
  }

  public abstract static class AssignabilityResult {

    public boolean isSuccessful() {
      return false;
    }

    public boolean isFailed() {
      return false;
    }

    public FailedAssignabilityResult asFailed() {
      return null;
    }
  }

  public static class SuccessfulAssignabilityResult extends AssignabilityResult {

    @Override
    public boolean isSuccessful() {
      return true;
    }
  }

  public static class FailedAssignabilityResult extends AssignabilityResult {

    private final String message;

    FailedAssignabilityResult(String message) {
      this.message = message;
    }

    public String getMessage() {
      return message;
    }

    @Override
    public boolean isFailed() {
      return true;
    }

    @Override
    public FailedAssignabilityResult asFailed() {
      return this;
    }
  }
}
