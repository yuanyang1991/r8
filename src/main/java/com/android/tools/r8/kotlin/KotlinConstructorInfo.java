// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.utils.FunctionUtils.forEachApply;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Reporter;
import java.util.List;
import kotlinx.metadata.KmClass;
import kotlinx.metadata.KmConstructor;
import kotlinx.metadata.jvm.JvmExtensionsKt;

// Holds information about a KmConstructor object.
public class KotlinConstructorInfo implements KotlinMethodLevelInfo {

  // Information from original KmValueParameter(s) if available.
  private final int flags;
  // Information about the value parameters.
  private final List<KotlinValueParameterInfo> valueParameterInfos;
  // Information about the signature.
  private final KotlinJvmMethodSignatureInfo signature;

  private KotlinConstructorInfo(
      int flags,
      List<KotlinValueParameterInfo> valueParameterInfos,
      KotlinJvmMethodSignatureInfo signature) {
    this.flags = flags;
    this.valueParameterInfos = valueParameterInfos;
    this.signature = signature;
  }

  public static KotlinConstructorInfo create(
      KmConstructor kmConstructor, DexItemFactory factory, Reporter reporter) {
    return new KotlinConstructorInfo(
        kmConstructor.getFlags(),
        KotlinValueParameterInfo.create(kmConstructor.getValueParameters(), factory, reporter),
        KotlinJvmMethodSignatureInfo.create(JvmExtensionsKt.getSignature(kmConstructor), factory));
  }

  public void rewrite(
      KmClass kmClass,
      DexEncodedMethod method,
      AppView<AppInfoWithLiveness> appView,
      NamingLens namingLens) {
    // Note that JvmExtensionsKt.setSignature does not have an overload for KmConstructorVisitor,
    // thus we rely on creating the KmConstructor manually.
    // TODO(b/154348683): Check for special flags to pass in.
    KmConstructor kmConstructor = new KmConstructor(flags);
    if (signature != null) {
      JvmExtensionsKt.setSignature(kmConstructor, signature.rewrite(method, appView, namingLens));
    }
    for (KotlinValueParameterInfo valueParameterInfo : valueParameterInfos) {
      valueParameterInfo.rewrite(kmConstructor::visitValueParameter, appView, namingLens);
    }
    kmClass.getConstructors().add(kmConstructor);
  }

  @Override
  public boolean isConstructor() {
    return true;
  }

  @Override
  public KotlinConstructorInfo asConstructor() {
    return this;
  }

  @Override
  public void trace(DexDefinitionSupplier definitionSupplier) {
    forEachApply(valueParameterInfos, param -> param::trace, definitionSupplier);
    if (signature != null) {
      signature.trace(definitionSupplier);
    }
  }
}
