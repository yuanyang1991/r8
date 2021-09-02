// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.graph.DexClasspathClass;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibraryRetargeterSynthesizerEventConsumer.DesugaredLibraryRetargeterL8SynthesizerEventConsumer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibraryWrapperSynthesizerEventConsumer.DesugaredLibraryCustomConversionEventConsumer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibraryWrapperSynthesizerEventConsumer.DesugaredLibraryL8ProgramWrapperSynthesizerEventConsumer;
import com.android.tools.r8.ir.desugar.itf.EmulatedInterfaceSynthesizerEventConsumer.L8ProgramEmulatedInterfaceSynthesizerEventConsumer;
import com.android.tools.r8.ir.desugar.records.RecordDesugaringEventConsumer;
import com.google.common.collect.Sets;
import java.util.Set;

public class CfClassSynthesizerDesugaringEventConsumer
    implements L8ProgramEmulatedInterfaceSynthesizerEventConsumer,
        DesugaredLibraryL8ProgramWrapperSynthesizerEventConsumer,
        DesugaredLibraryRetargeterL8SynthesizerEventConsumer,
        DesugaredLibraryCustomConversionEventConsumer,
        RecordDesugaringEventConsumer {

  private Set<DexProgramClass> synthesizedClasses = Sets.newConcurrentHashSet();

  @Override
  public void acceptProgramEmulatedInterface(DexProgramClass clazz) {
    synthesizedClasses.add(clazz);
  }

  @Override
  public void acceptWrapperProgramClass(DexProgramClass clazz) {
    synthesizedClasses.add(clazz);
  }

  @Override
  public void acceptDesugaredLibraryRetargeterDispatchProgramClass(DexProgramClass clazz) {
    synthesizedClasses.add(clazz);
  }

  @Override
  public void acceptRecordClass(DexProgramClass clazz) {
    synthesizedClasses.add(clazz);
  }

  @Override
  public void acceptCustomConversionClasspathClass(DexClasspathClass clazz) {
    // Intentionally empty.
  }

  public Set<DexProgramClass> getSynthesizedClasses() {
    return synthesizedClasses;
  }
}
