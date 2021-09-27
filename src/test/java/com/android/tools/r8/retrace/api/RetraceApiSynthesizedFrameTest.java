// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.api;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.retrace.ProguardMapProducer;
import com.android.tools.r8.retrace.RetraceFrameElement;
import com.android.tools.r8.retrace.RetraceStackTraceContext;
import com.android.tools.r8.retrace.RetracedMethodReference;
import com.android.tools.r8.retrace.Retracer;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RetraceApiSynthesizedFrameTest extends RetraceApiTestBase {

  public RetraceApiSynthesizedFrameTest(TestParameters parameters) {
    super(parameters);
  }

  @Override
  protected Class<? extends RetraceApiBinaryTest> binaryTestClass() {
    return ApiTest.class;
  }

  public static class ApiTest implements RetraceApiBinaryTest {

    private final String mapping =
        "# { id: 'com.android.tools.r8.mapping', version: '1.0' }\n"
            + "some.Class -> a:\n"
            + "  3:3:int other.strawberry(int):101:101 -> a\n"
            + "  3:3:int mango(float):28 -> a\n"
            + "  # { id: 'com.android.tools.r8.synthesized' }";

    @Test
    public void testSyntheticClass() {
      List<RetraceFrameElement> frameResults =
          Retracer.createDefault(
                  ProguardMapProducer.fromString(mapping), new DiagnosticsHandler() {})
              .retraceClass(Reference.classFromTypeName("a"))
              .stream()
              .flatMap(
                  element ->
                      element
                          .lookupFrame(RetraceStackTraceContext.empty(), OptionalInt.of(3), "a")
                          .stream())
              .collect(Collectors.toList());
      assertEquals(1, frameResults.size());
      RetraceFrameElement retraceFrameElement = frameResults.get(0);
      List<RetracedMethodReference> allFrames = new ArrayList<>();
      retraceFrameElement.visitAllFrames((method, ignored) -> allFrames.add(method));
      assertEquals(2, allFrames.size());
      List<RetracedMethodReference> nonSyntheticFrames = new ArrayList<>();
      retraceFrameElement.visitRewrittenFrames(
          (method, ignored) -> nonSyntheticFrames.add(method));
      assertEquals(1, nonSyntheticFrames.size());
      assertEquals(nonSyntheticFrames.get(0), allFrames.get(0));
      assertEquals("mango", allFrames.get(1).getMethodName());
    }
  }
}
