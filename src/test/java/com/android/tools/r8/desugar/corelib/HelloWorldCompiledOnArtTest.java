// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.corelib;

import static com.android.tools.r8.utils.FileUtils.JAR_EXTENSION;
import static junit.framework.TestCase.assertEquals;

import com.android.tools.r8.D8;
import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.R8;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class HelloWorldCompiledOnArtTest extends CoreLibDesugarTestBase {

  private final TestParameters parameters;

  // TODO(b/142621961): Parametrize at least L and P instead of just P.
  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDexRuntime(Version.V9_0_0)
        .withApiLevelsStartingAtIncluding(AndroidApiLevel.L)
        .build();
  }

  public HelloWorldCompiledOnArtTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private static String commandLinePathFor(String string) {
    return Paths.get(string).toAbsolutePath().toString();
  }

  private static final String HELLO_KEEP =
      commandLinePathFor("src/test/examples/hello/keep-rules.txt");
  private static final String HELLO_PATH =
      commandLinePathFor(ToolHelper.EXAMPLES_BUILD_DIR + "hello" + JAR_EXTENSION);

  @Test
  public void testHelloCompiledWithR8Dex() throws Exception {
    Path helloOutput = temp.newFolder("helloOutput").toPath().resolve("out.zip").toAbsolutePath();
    compileR8ToDexWithD8()
        .run(
            parameters.getRuntime(),
            R8.class,
            "--release",
            "--output",
            helloOutput.toString(),
            "--lib",
            commandLinePathFor(ToolHelper.JAVA_8_RUNTIME),
            "--pg-conf",
            HELLO_KEEP,
            HELLO_PATH)
        .assertSuccess();
    verifyResult(helloOutput);
  }

  @Test
  public void testHelloCompiledWithD8Dex() throws Exception {
    Path helloOutput = temp.newFolder("helloOutput").toPath().resolve("out.zip").toAbsolutePath();
    compileR8ToDexWithD8()
        .run(
            parameters.getRuntime(),
            D8.class,
            "--release",
            "--output",
            helloOutput.toString(),
            "--lib",
            commandLinePathFor(ToolHelper.JAVA_8_RUNTIME),
            HELLO_PATH)
        .assertSuccess();
    verifyResult(helloOutput);
  }

  private void verifyResult(Path helloOutput) throws IOException {
    ProcessResult processResult = ToolHelper.runArtRaw(helloOutput.toString(), "hello.Hello");
    assertEquals(StringUtils.lines("Hello, world"), processResult.stdout);
  }

  private D8TestCompileResult compileR8ToDexWithD8()
      throws com.android.tools.r8.CompilationFailedException {
    return testForD8()
        .addProgramFiles(ToolHelper.R8_WITH_RELOCATED_DEPS_JAR)
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring(parameters.getApiLevel())
        .compile()
        .addDesugaredCoreLibraryRunClassPath(this::buildDesugaredLibrary, parameters.getApiLevel())
        .withArt6Plus64BitsLib()
        .withArtFrameworks();
  }
}
