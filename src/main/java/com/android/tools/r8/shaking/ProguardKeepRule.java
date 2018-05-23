// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ProguardKeepRule extends ProguardConfigurationRule {

  public static class Builder extends ProguardClassSpecification.Builder {

    private ProguardKeepRuleType type;
    private final ProguardKeepRuleModifiers.Builder modifiersBuilder =
        ProguardKeepRuleModifiers.builder();

    protected Builder() {}

    public void setType(ProguardKeepRuleType type) {
      this.type = type;
    }

    public ProguardKeepRuleModifiers.Builder getModifiersBuilder() {
      return modifiersBuilder;
    }

    public ProguardKeepRule build() {
      return new ProguardKeepRule(classAnnotation, classAccessFlags, negatedClassAccessFlags,
          classTypeNegated, classType, classNames, inheritanceAnnotation, inheritanceClassName,
          inheritanceIsExtends, memberRules, type, modifiersBuilder.build());
    }
  }

  private final ProguardKeepRuleType type;
  private final ProguardKeepRuleModifiers modifiers;

  protected ProguardKeepRule(
      ProguardTypeMatcher classAnnotation,
      ProguardAccessFlags classAccessFlags,
      ProguardAccessFlags negatedClassAccessFlags,
      boolean classTypeNegated,
      ProguardClassType classType,
      ProguardClassNameList classNames,
      ProguardTypeMatcher inheritanceAnnotation,
      ProguardTypeMatcher inheritanceClassName,
      boolean inheritanceIsExtends,
      List<ProguardMemberRule> memberRules,
      ProguardKeepRuleType type,
      ProguardKeepRuleModifiers modifiers) {
    super(classAnnotation, classAccessFlags, negatedClassAccessFlags, classTypeNegated, classType,
        classNames, inheritanceAnnotation, inheritanceClassName, inheritanceIsExtends, memberRules);
    this.type = type;
    this.modifiers = modifiers;
  }

  /**
   * Create a new empty builder.
   */
  public static Builder builder() {
    return new Builder();
  }

  public ProguardKeepRuleType getType() {
    return type;
  }

  public ProguardKeepRuleModifiers getModifiers() {
    return modifiers;
  }

  protected ProguardKeepRule materialize() {
    return new ProguardKeepRule(
        getClassAnnotation(),
        getClassAccessFlags(),
        getNegatedClassAccessFlags(),
        getClassTypeNegated(),
        getClassType(),
        getClassNames() == null ? null : getClassNames().materialize(),
        getInheritanceAnnotation() == null ? null : getInheritanceAnnotation().materialize(),
        getInheritanceClassName() == null ? null : getInheritanceClassName().materialize(),
        getInheritanceIsExtends(),
        getMemberRules() == null ? null :
            getMemberRules().stream()
                .map(ProguardMemberRule::materialize).collect(Collectors.toList()),
        getType(),
        getModifiers());
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ProguardKeepRule)) {
      return false;
    }
    ProguardKeepRule that = (ProguardKeepRule) o;

    if (type != that.type) {
      return false;
    }
    if (!modifiers.equals(that.modifiers)) {
      return false;
    }
    return super.equals(that);
  }

  @Override
  public int hashCode() {
    // Used multiplier 3 to avoid too much overflow when computing hashCode.
    int result = type.hashCode();
    result = 3 * result + modifiers.hashCode();
    result = 3 * result + super.hashCode();
    return result;
  }

  static void appendNonEmpty(StringBuilder builder, String pre, Object item, String post) {
    if (item == null) {
      return;
    }
    String text = item.toString();
    if (!text.isEmpty()) {
      if (pre != null) {
        builder.append(pre);
      }
      builder.append(text);
      if (post != null) {
        builder.append(post);
      }
    }
  }

  @Override
  String typeString() {
    return type.toString();
  }

  @Override
  String modifierString() {
    return modifiers.toString();
  }

  public static ProguardKeepRule defaultKeepAllRule(
      Consumer<ProguardKeepRuleModifiers.Builder> modifiers) {
    ProguardKeepRule.Builder builder = ProguardKeepRule.builder();
    builder.setClassType(ProguardClassType.CLASS);
    builder.matchAllSpecification();
    builder.setType(ProguardKeepRuleType.KEEP);
    modifiers.accept(builder.getModifiersBuilder());
    return builder.build();
  }
}
