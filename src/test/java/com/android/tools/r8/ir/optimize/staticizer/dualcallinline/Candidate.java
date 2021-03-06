// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.staticizer.dualcallinline;

import com.android.tools.r8.KeepConstantArguments;
import com.android.tools.r8.NeverInline;

public class Candidate {
  @NeverInline
  public String foo() {
    return bar("Candidate::foo()");
  }

  @KeepConstantArguments
  @NeverInline
  public String bar(String other) {
    return "Candidate::bar(" + other + ")";
  }
}
