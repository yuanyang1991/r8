// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.resolution.singletarget;

import static com.android.tools.r8.ToolHelper.getMostRecentAndroidJar;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.type.DynamicTypeWithUpperBound;
import com.android.tools.r8.optimize.interfaces.collection.NonEmptyOpenClosedInterfacesCollection;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SuccessAndInvalidLookupTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public SuccessAndInvalidLookupTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void testSingleTargetWithInvalidInvokeInterfaceInvoke() throws Exception {
    AppView<AppInfoWithLiveness> appView =
        computeAppViewWithLiveness(
            buildClasses(I.class, A.class, Main.class)
                .addLibraryFile(getMostRecentAndroidJar())
                .build(),
            factory ->
                buildConfigForRules(factory, buildKeepRuleForClassAndMethods(Main.class, factory)));
    AppInfoWithLiveness appInfo = appView.appInfo();
    DexType typeMain = buildType(Main.class, appInfo.dexItemFactory());
    DexMethod mainMethodReference =
        buildMethod(Main.class.getDeclaredMethod("main", String[].class), appInfo.dexItemFactory());
    ProgramMethod mainMethod =
        appInfo.definitionForProgramType(typeMain).lookupProgramMethod(mainMethodReference);
    DexType typeA = buildType(A.class, appInfo.dexItemFactory());
    DynamicType dynamicTypeA =
        DynamicTypeWithUpperBound.create(appView, typeA.toTypeElement(appView));
    DexMethod fooA = buildNullaryVoidMethod(A.class, "foo", appInfo.dexItemFactory());
    DexEncodedMethod singleTarget =
        appInfo.lookupSingleVirtualTarget(
            appView, fooA, mainMethod, false, t -> false, dynamicTypeA);
    assertNotNull(singleTarget);
    assertEquals(fooA, singleTarget.getReference());
    DexEncodedMethod invalidSingleTarget =
        appInfo.lookupSingleVirtualTarget(
            appView, fooA, mainMethod, true, t -> false, dynamicTypeA);
    assertNull(invalidSingleTarget);
  }

  @Test
  public void testSingleTargetWithInvalidInvokeVirtualInvoke() throws Exception {
    AppView<AppInfoWithLiveness> appView =
        computeAppViewWithLiveness(
            buildClasses(I.class, A.class, Main.class)
                .addLibraryFile(getMostRecentAndroidJar())
                .build(),
            factory ->
                buildConfigForRules(factory, buildKeepRuleForClassAndMethods(Main.class, factory)));
    appView.setOpenClosedInterfacesCollection(
        new NonEmptyOpenClosedInterfacesCollection(Collections.emptySet()));
    AppInfoWithLiveness appInfo = appView.appInfo();
    DexType typeMain = buildType(Main.class, appInfo.dexItemFactory());
    DexMethod mainMethodReference =
        buildMethod(Main.class.getDeclaredMethod("main", String[].class), appInfo.dexItemFactory());
    ProgramMethod mainMethod =
        appInfo.definitionForProgramType(typeMain).lookupProgramMethod(mainMethodReference);
    DexType typeA = buildType(I.class, appInfo.dexItemFactory());
    DynamicType dynamicTypeA =
        DynamicTypeWithUpperBound.create(appView, typeA.toTypeElement(appView));
    DexMethod fooI = buildNullaryVoidMethod(I.class, "foo", appInfo.dexItemFactory());
    DexMethod fooA = buildNullaryVoidMethod(A.class, "foo", appInfo.dexItemFactory());
    DexEncodedMethod singleTarget =
        appInfo.lookupSingleVirtualTarget(
            appView, fooI, mainMethod, true, t -> false, dynamicTypeA);
    assertNotNull(singleTarget);
    assertEquals(fooA, singleTarget.getReference());
    DexEncodedMethod invalidSingleTarget =
        appInfo.lookupSingleVirtualTarget(
            appView, fooI, mainMethod, false, t -> false, dynamicTypeA);
    assertNull(invalidSingleTarget);
  }

  public interface I {

    void foo();
  }

  public static class A implements I {

    @Override
    public void foo() {
      System.out.println("A.foo");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      new A();
    }
  }
}
