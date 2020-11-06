// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal.dispatch;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.classmerging.horizontal.HorizontalClassMergingTestBase;
import org.junit.Test;

public class OverrideDefaultMethodTest extends HorizontalClassMergingTestBase {
  public OverrideDefaultMethodTest(
      TestParameters parameters, boolean enableHorizontalClassMerging) {
    super(parameters, enableHorizontalClassMerging);
  }

  @Test
  public void testR8() throws Exception {
    // TODO: fix this test.
    assumeTrue(!enableHorizontalClassMerging);

    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addOptionsModification(
            options -> options.enableHorizontalClassMerging = enableHorizontalClassMerging)
        .enableInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters.getApiLevel())
        .addHorizontallyMergedClassesInspectorIf(
            enableHorizontalClassMerging, inspector -> inspector.assertNoClassesMerged())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("I", "B", "J")
        .inspect(
            codeInspector -> {
              assertThat(codeInspector.clazz(I.class), isPresent());
              assertThat(codeInspector.clazz(J.class), isPresent());
              assertThat(codeInspector.clazz(A.class), isPresent());
              assertThat(codeInspector.clazz(B.class), isPresent());
              assertThat(codeInspector.clazz(C.class), isPresent());
            });
  }

  @NoVerticalClassMerging
  interface I {
    @NeverInline
    default void m() {
      System.out.println("I");
    }
  }

  public static class A implements I {}

  public static class B implements I {
    @NeverInline
    @Override
    public void m() {
      System.out.println("B");
    }
  }

  @NoVerticalClassMerging
  interface J extends I {
    default void m() {
      System.out.println("J");
    }
  }

  public static class C extends A implements J {}

  public static class Main {
    @NeverInline
    public static void doI(I i) {
      i.m();
    }

    public static void main(String[] args) {
      doI(new A());
      doI(new B());
      doI(new C());
    }
  }
}