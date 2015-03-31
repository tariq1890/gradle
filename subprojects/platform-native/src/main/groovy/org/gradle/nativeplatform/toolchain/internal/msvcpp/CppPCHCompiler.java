/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.nativeplatform.toolchain.internal.msvcpp;

import org.gradle.api.Transformer;
import org.gradle.internal.operations.BuildOperationProcessor;
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolContext;
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolInvocationWorker;
import org.gradle.nativeplatform.toolchain.internal.compilespec.CppPCHCompileSpec;

import java.io.File;
import java.util.Collections;
import java.util.List;

public class CppPCHCompiler extends VisualCppNativeCompiler<CppPCHCompileSpec> {
    public CppPCHCompiler(BuildOperationProcessor buildOperationProcessor, CommandLineToolInvocationWorker commandLineToolInvocationWorker, CommandLineToolContext invocationContext, Transformer<CppPCHCompileSpec, CppPCHCompileSpec> specTransformer, String objectFileExtension, boolean useCommandFile) {
        super(buildOperationProcessor, commandLineToolInvocationWorker, invocationContext, new VisualCppPCHCompilerArgsTransformer<CppPCHCompileSpec>(), specTransformer, objectFileExtension, useCommandFile);
    }

    @Override
    protected List<String> getOutputArgs(CppPCHCompileSpec spec, File outputFile) {
        return Collections.singletonList("/Fp" + outputFile.getAbsolutePath());
    }
}