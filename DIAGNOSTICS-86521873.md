# LangChain4j integration run — FAILED

- commit: `865218739e14865104217a5125d6be6e7c1799bc`
- run: 37124686417 (attempt 1)

## build log, last 400 lines

```
	at org.junit.jupiter.engine.extension.TimeoutExtension.intercept(TimeoutExtension.java:161)
	at org.junit.jupiter.engine.extension.TimeoutExtension.interceptTestableMethod(TimeoutExtension.java:152)
	at org.junit.jupiter.engine.extension.TimeoutExtension.interceptTestMethod(TimeoutExtension.java:91)
	at org.junit.jupiter.engine.execution.InterceptingExecutableInvoker$ReflectiveInterceptorCall.lambda$ofVoidMethod$0(InterceptingExecutableInvoker.java:112)
	at org.junit.jupiter.engine.execution.InterceptingExecutableInvoker.lambda$invoke$0(InterceptingExecutableInvoker.java:94)
	at org.junit.jupiter.engine.execution.InvocationInterceptorChain$InterceptedInvocation.proceed(InvocationInterceptorChain.java:106)
	at org.junit.jupiter.engine.execution.InvocationInterceptorChain.proceed(InvocationInterceptorChain.java:64)
	at org.junit.jupiter.engine.execution.InvocationInterceptorChain.chainAndInvoke(InvocationInterceptorChain.java:45)
	at org.junit.jupiter.engine.execution.InvocationInterceptorChain.invoke(InvocationInterceptorChain.java:37)
	at org.junit.jupiter.engine.execution.InterceptingExecutableInvoker.invoke(InterceptingExecutableInvoker.java:93)
	at org.junit.jupiter.engine.execution.InterceptingExecutableInvoker.invoke(InterceptingExecutableInvoker.java:87)
	at org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor.lambda$invokeTestMethod$7(TestMethodTestDescriptor.java:216)
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:73)
	at org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor.invokeTestMethod(TestMethodTestDescriptor.java:212)
	at org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor.execute(TestMethodTestDescriptor.java:137)
	at org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor.execute(TestMethodTestDescriptor.java:69)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$6(NodeTestTask.java:156)
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:73)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$8(NodeTestTask.java:146)
	at org.junit.platform.engine.support.hierarchical.Node.around(Node.java:137)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$9(NodeTestTask.java:144)
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:73)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.executeRecursively(NodeTestTask.java:143)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.execute(NodeTestTask.java:100)
	at java.base/java.util.ArrayList.forEach(ArrayList.java:1596)
	at org.junit.platform.engine.support.hierarchical.SameThreadHierarchicalTestExecutorService.invokeAll(SameThreadHierarchicalTestExecutorService.java:41)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$6(NodeTestTask.java:160)
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:73)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$8(NodeTestTask.java:146)
	at org.junit.platform.engine.support.hierarchical.Node.around(Node.java:137)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$9(NodeTestTask.java:144)
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:73)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.executeRecursively(NodeTestTask.java:143)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.execute(NodeTestTask.java:100)
	at java.base/java.util.ArrayList.forEach(ArrayList.java:1596)
	at org.junit.platform.engine.support.hierarchical.SameThreadHierarchicalTestExecutorService.invokeAll(SameThreadHierarchicalTestExecutorService.java:41)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$6(NodeTestTask.java:160)
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:73)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$8(NodeTestTask.java:146)
	at org.junit.platform.engine.support.hierarchical.Node.around(Node.java:137)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$9(NodeTestTask.java:144)
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:73)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.executeRecursively(NodeTestTask.java:143)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.execute(NodeTestTask.java:100)
	at org.junit.platform.engine.support.hierarchical.SameThreadHierarchicalTestExecutorService.submit(SameThreadHierarchicalTestExecutorService.java:35)
	at org.junit.platform.engine.support.hierarchical.HierarchicalTestExecutor.execute(HierarchicalTestExecutor.java:57)
	at org.junit.platform.engine.support.hierarchical.HierarchicalTestEngine.execute(HierarchicalTestEngine.java:54)
	at org.junit.platform.launcher.core.EngineExecutionOrchestrator.execute(EngineExecutionOrchestrator.java:201)
	at org.junit.platform.launcher.core.EngineExecutionOrchestrator.execute(EngineExecutionOrchestrator.java:170)
	at org.junit.platform.launcher.core.EngineExecutionOrchestrator.execute(EngineExecutionOrchestrator.java:94)
	at org.junit.platform.launcher.core.EngineExecutionOrchestrator.lambda$execute$0(EngineExecutionOrchestrator.java:59)
	at org.junit.platform.launcher.core.EngineExecutionOrchestrator.withInterceptedStreams(EngineExecutionOrchestrator.java:142)
	at org.junit.platform.launcher.core.EngineExecutionOrchestrator.execute(EngineExecutionOrchestrator.java:58)
	at org.junit.platform.launcher.core.DefaultLauncher.execute(DefaultLauncher.java:103)
	at org.junit.platform.launcher.core.DefaultLauncher.execute(DefaultLauncher.java:85)
	at org.junit.platform.launcher.core.DelegatingLauncher.execute(DelegatingLauncher.java:47)
	at org.junit.platform.launcher.core.InterceptingLauncher.lambda$execute$1(InterceptingLauncher.java:39)
	at org.junit.platform.launcher.core.ClasspathAlignmentCheckingLauncherInterceptor.intercept(ClasspathAlignmentCheckingLauncherInterceptor.java:25)
	at org.junit.platform.launcher.core.InterceptingLauncher.execute(InterceptingLauncher.java:38)
	at org.junit.platform.launcher.core.DelegatingLauncher.execute(DelegatingLauncher.java:47)
	at org.apache.maven.surefire.junitplatform.LauncherAdapter.executeWithoutCancellationToken(LauncherAdapter.java:60)
	at org.apache.maven.surefire.junitplatform.LauncherAdapter.execute(LauncherAdapter.java:52)
	at org.apache.maven.surefire.junitplatform.JUnitPlatformProvider.execute(JUnitPlatformProvider.java:203)
	at org.apache.maven.surefire.junitplatform.JUnitPlatformProvider.invokeAllTests(JUnitPlatformProvider.java:168)
	at org.apache.maven.surefire.junitplatform.JUnitPlatformProvider.invoke(JUnitPlatformProvider.java:136)
	at org.apache.maven.surefire.booter.ForkedBooter.runSuitesInProcess(ForkedBooter.java:385)
	at org.apache.maven.surefire.booter.ForkedBooter.execute(ForkedBooter.java:162)
	at org.apache.maven.surefire.booter.ForkedBooter.run(ForkedBooter.java:507)
	at org.apache.maven.surefire.booter.ForkedBooter.main(ForkedBooter.java:495)
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.030 s -- in io.github.godhuino1.tomograph.core.InstrumentationEngineTest
[INFO] Running io.github.godhuino1.tomograph.core.SignatureRoutingTest
[tomograph] ERROR module broken failed on io/github/godhuino1/tomograph/core/SignatureRoutingTest$BlockingModelImpl; discarding the whole rewrite for this class
java.lang.IllegalStateException: simulated module failure
	at io.github.godhuino1.tomograph.core.SignatureRoutingTest$RecordingModule.instrument(SignatureRoutingTest.java:120)
	at io.github.godhuino1.tomograph.core.InstrumentationEngine.transform(InstrumentationEngine.java:146)
	at io.github.godhuino1.tomograph.core.SignatureRoutingTest.aSignatureRoutedModuleThatThrowsLeavesTheClassUntouched(SignatureRoutingTest.java:225)
	at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:103)
	at java.base/java.lang.reflect.Method.invoke(Method.java:580)
	at org.junit.platform.commons.util.ReflectionUtils.invokeMethod(ReflectionUtils.java:775)
	at org.junit.platform.commons.support.ReflectionSupport.invokeMethod(ReflectionSupport.java:479)
	at org.junit.jupiter.engine.execution.MethodInvocation.proceed(MethodInvocation.java:60)
	at org.junit.jupiter.engine.execution.InvocationInterceptorChain$ValidatingInvocation.proceed(InvocationInterceptorChain.java:131)
	at org.junit.jupiter.engine.extension.TimeoutExtension.intercept(TimeoutExtension.java:161)
	at org.junit.jupiter.engine.extension.TimeoutExtension.interceptTestableMethod(TimeoutExtension.java:152)
	at org.junit.jupiter.engine.extension.TimeoutExtension.interceptTestMethod(TimeoutExtension.java:91)
	at org.junit.jupiter.engine.execution.InterceptingExecutableInvoker$ReflectiveInterceptorCall.lambda$ofVoidMethod$0(InterceptingExecutableInvoker.java:112)
	at org.junit.jupiter.engine.execution.InterceptingExecutableInvoker.lambda$invoke$0(InterceptingExecutableInvoker.java:94)
	at org.junit.jupiter.engine.execution.InvocationInterceptorChain$InterceptedInvocation.proceed(InvocationInterceptorChain.java:106)
	at org.junit.jupiter.engine.execution.InvocationInterceptorChain.proceed(InvocationInterceptorChain.java:64)
	at org.junit.jupiter.engine.execution.InvocationInterceptorChain.chainAndInvoke(InvocationInterceptorChain.java:45)
	at org.junit.jupiter.engine.execution.InvocationInterceptorChain.invoke(InvocationInterceptorChain.java:37)
	at org.junit.jupiter.engine.execution.InterceptingExecutableInvoker.invoke(InterceptingExecutableInvoker.java:93)
	at org.junit.jupiter.engine.execution.InterceptingExecutableInvoker.invoke(InterceptingExecutableInvoker.java:87)
	at org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor.lambda$invokeTestMethod$7(TestMethodTestDescriptor.java:216)
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:73)
	at org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor.invokeTestMethod(TestMethodTestDescriptor.java:212)
	at org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor.execute(TestMethodTestDescriptor.java:137)
	at org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor.execute(TestMethodTestDescriptor.java:69)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$6(NodeTestTask.java:156)
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:73)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$8(NodeTestTask.java:146)
	at org.junit.platform.engine.support.hierarchical.Node.around(Node.java:137)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$9(NodeTestTask.java:144)
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:73)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.executeRecursively(NodeTestTask.java:143)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.execute(NodeTestTask.java:100)
	at java.base/java.util.ArrayList.forEach(ArrayList.java:1596)
	at org.junit.platform.engine.support.hierarchical.SameThreadHierarchicalTestExecutorService.invokeAll(SameThreadHierarchicalTestExecutorService.java:41)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$6(NodeTestTask.java:160)
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:73)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$8(NodeTestTask.java:146)
	at org.junit.platform.engine.support.hierarchical.Node.around(Node.java:137)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$9(NodeTestTask.java:144)
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:73)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.executeRecursively(NodeTestTask.java:143)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.execute(NodeTestTask.java:100)
	at java.base/java.util.ArrayList.forEach(ArrayList.java:1596)
	at org.junit.platform.engine.support.hierarchical.SameThreadHierarchicalTestExecutorService.invokeAll(SameThreadHierarchicalTestExecutorService.java:41)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$6(NodeTestTask.java:160)
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:73)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$8(NodeTestTask.java:146)
	at org.junit.platform.engine.support.hierarchical.Node.around(Node.java:137)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$9(NodeTestTask.java:144)
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:73)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.executeRecursively(NodeTestTask.java:143)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.execute(NodeTestTask.java:100)
	at org.junit.platform.engine.support.hierarchical.SameThreadHierarchicalTestExecutorService.submit(SameThreadHierarchicalTestExecutorService.java:35)
	at org.junit.platform.engine.support.hierarchical.HierarchicalTestExecutor.execute(HierarchicalTestExecutor.java:57)
	at org.junit.platform.engine.support.hierarchical.HierarchicalTestEngine.execute(HierarchicalTestEngine.java:54)
	at org.junit.platform.launcher.core.EngineExecutionOrchestrator.execute(EngineExecutionOrchestrator.java:201)
	at org.junit.platform.launcher.core.EngineExecutionOrchestrator.execute(EngineExecutionOrchestrator.java:170)
	at org.junit.platform.launcher.core.EngineExecutionOrchestrator.execute(EngineExecutionOrchestrator.java:94)
	at org.junit.platform.launcher.core.EngineExecutionOrchestrator.lambda$execute$0(EngineExecutionOrchestrator.java:59)
	at org.junit.platform.launcher.core.EngineExecutionOrchestrator.withInterceptedStreams(EngineExecutionOrchestrator.java:142)
	at org.junit.platform.launcher.core.EngineExecutionOrchestrator.execute(EngineExecutionOrchestrator.java:58)
	at org.junit.platform.launcher.core.DefaultLauncher.execute(DefaultLauncher.java:103)
	at org.junit.platform.launcher.core.DefaultLauncher.execute(DefaultLauncher.java:85)
	at org.junit.platform.launcher.core.DelegatingLauncher.execute(DelegatingLauncher.java:47)
	at org.junit.platform.launcher.core.InterceptingLauncher.lambda$execute$1(InterceptingLauncher.java:39)
	at org.junit.platform.launcher.core.ClasspathAlignmentCheckingLauncherInterceptor.intercept(ClasspathAlignmentCheckingLauncherInterceptor.java:25)
	at org.junit.platform.launcher.core.InterceptingLauncher.execute(InterceptingLauncher.java:38)
	at org.junit.platform.launcher.core.DelegatingLauncher.execute(DelegatingLauncher.java:47)
	at org.apache.maven.surefire.junitplatform.LauncherAdapter.executeWithoutCancellationToken(LauncherAdapter.java:60)
	at org.apache.maven.surefire.junitplatform.LauncherAdapter.execute(LauncherAdapter.java:52)
	at org.apache.maven.surefire.junitplatform.JUnitPlatformProvider.execute(JUnitPlatformProvider.java:203)
	at org.apache.maven.surefire.junitplatform.JUnitPlatformProvider.invokeAllTests(JUnitPlatformProvider.java:168)
	at org.apache.maven.surefire.junitplatform.JUnitPlatformProvider.invoke(JUnitPlatformProvider.java:136)
	at org.apache.maven.surefire.booter.ForkedBooter.runSuitesInProcess(ForkedBooter.java:385)
	at org.apache.maven.surefire.booter.ForkedBooter.execute(ForkedBooter.java:162)
	at org.apache.maven.surefire.booter.ForkedBooter.run(ForkedBooter.java:507)
	at org.apache.maven.surefire.booter.ForkedBooter.main(ForkedBooter.java:495)
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.030 s -- in io.github.godhuino1.tomograph.core.SignatureRoutingTest
[INFO] Running io.github.godhuino1.tomograph.core.RoutingCostTest
[cost] full descriptor check (the second stage): 12499 ns per class of 8860 bytes; 12 ms per 1000 classes loaded, per cut point
[cost] byte scan, name present (the hit case): 1564 ns per class of 3053 bytes; 1 ms per 1000 classes loaded, per cut point
[cost] byte scan, name absent (the common case): 13762 ns per class of 8860 bytes; 13 ms per 1000 classes loaded, per cut point
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.478 s -- in io.github.godhuino1.tomograph.core.RoutingCostTest
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 36, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] 
[INFO] --- jar:3.5.0:jar (default-jar) @ tomograph-core ---
[INFO] Building jar: /home/runner/work/tomograph/tomograph/tomograph-core/target/tomograph-core-0.1.0-SNAPSHOT.jar
[INFO] 
[INFO] ------------< io.github.godhuino1-lang:tomograph-javaagent >------------
[INFO] Building Tomograph :: Java Agent 0.1.0-SNAPSHOT                   [6/11]
[INFO]   from tomograph-javaagent/pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] --- resources:3.4.0:resources (default-resources) @ tomograph-javaagent ---
[INFO] skip non existing resourceDirectory /home/runner/work/tomograph/tomograph/tomograph-javaagent/src/main/resources
[INFO] 
[INFO] --- compiler:3.13.0:compile (default-compile) @ tomograph-javaagent ---
[INFO] Recompiling the module because of changed dependency.
[INFO] Compiling 1 source file with javac [debug release 17] to target/classes
[INFO] 
[INFO] --- resources:3.4.0:testResources (default-testResources) @ tomograph-javaagent ---
[INFO] skip non existing resourceDirectory /home/runner/work/tomograph/tomograph/tomograph-javaagent/src/test/resources
[INFO] 
[INFO] --- compiler:3.13.0:testCompile (default-testCompile) @ tomograph-javaagent ---
[INFO] No sources to compile
[INFO] 
[INFO] --- surefire:3.5.6:test (default-test) @ tomograph-javaagent ---
[INFO] No tests to run.
[INFO] 
[INFO] --- jar:3.5.0:jar (default-jar) @ tomograph-javaagent ---
[INFO] Building jar: /home/runner/work/tomograph/tomograph/tomograph-javaagent/target/tomograph-agent.jar
[INFO] 
[INFO] --- shade:3.6.0:shade (default) @ tomograph-javaagent ---
[INFO] Including io.github.godhuino1-lang:tomograph-core:jar:0.1.0-SNAPSHOT in the shaded jar.
[INFO] Including io.github.godhuino1-lang:tomograph-api:jar:0.1.0-SNAPSHOT in the shaded jar.
[INFO] Including io.github.godhuino1-lang:tomograph-exporter-otlp:jar:0.1.0-SNAPSHOT in the shaded jar.
[INFO] Including io.github.godhuino1-lang:tomograph-semconv:jar:0.1.0-SNAPSHOT in the shaded jar.
[WARNING] tomograph-agent.jar, tomograph-api-0.1.0-SNAPSHOT.jar, tomograph-core-0.1.0-SNAPSHOT.jar, tomograph-exporter-otlp-0.1.0-SNAPSHOT.jar, tomograph-semconv-0.1.0-SNAPSHOT.jar define 1 overlapping resource: 
[WARNING]   - META-INF/MANIFEST.MF
[WARNING] maven-shade-plugin has detected that some files are
[WARNING] present in two or more JARs. When this happens, only one
[WARNING] single version of the file is copied to the uber jar.
[WARNING] Usually this is not harmful and you can skip these warnings,
[WARNING] otherwise try to manually exclude artifacts based on
[WARNING] mvn dependency:tree -Ddetail=true and the above output.
[WARNING] See https://maven.apache.org/plugins/maven-shade-plugin/
[INFO] Replacing original artifact with shaded artifact.
[INFO] Replacing /home/runner/work/tomograph/tomograph/tomograph-javaagent/target/tomograph-agent.jar with /home/runner/work/tomograph/tomograph/tomograph-javaagent/target/tomograph-javaagent-0.1.0-SNAPSHOT-shaded.jar
[INFO] 
[INFO] ---< io.github.godhuino1-lang:tomograph-instrumentation-langchain4j >---
[INFO] Building Tomograph :: Instrumentation :: LangChain4j 0.1.0-SNAPSHOT [7/11]
[INFO]   from tomograph-instrumentation-langchain4j/pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] --- resources:3.4.0:resources (default-resources) @ tomograph-instrumentation-langchain4j ---
[INFO] Copying 1 resource from src/main/resources to target/classes
[INFO] 
[INFO] --- compiler:3.13.0:compile (default-compile) @ tomograph-instrumentation-langchain4j ---
[INFO] Recompiling the module because of changed dependency.
[INFO] Compiling 3 source files with javac [debug release 17] to target/classes
[INFO] 
[INFO] --- resources:3.4.0:testResources (default-testResources) @ tomograph-instrumentation-langchain4j ---
[INFO] skip non existing resourceDirectory /home/runner/work/tomograph/tomograph/tomograph-instrumentation-langchain4j/src/test/resources
[INFO] 
[INFO] --- compiler:3.13.0:testCompile (default-testCompile) @ tomograph-instrumentation-langchain4j ---
[INFO] Recompiling the module because of changed dependency.
[INFO] Compiling 1 source file with javac [debug release 17] to target/test-classes
[INFO] 
[INFO] --- surefire:3.5.6:test (default-test) @ tomograph-instrumentation-langchain4j ---
[INFO] Using auto detected provider org.apache.maven.surefire.junitplatform.JUnitPlatformProvider
[INFO] 
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running io.github.godhuino1.tomograph.instrumentation.langchain4j.LangChain4jModuleRewriteTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.062 s -- in io.github.godhuino1.tomograph.instrumentation.langchain4j.LangChain4jModuleRewriteTest
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] 
[INFO] --- jar:3.5.0:jar (default-jar) @ tomograph-instrumentation-langchain4j ---
[INFO] Building jar: /home/runner/work/tomograph/tomograph/tomograph-instrumentation-langchain4j/target/tomograph-instrumentation-langchain4j-0.1.0-SNAPSHOT.jar
[INFO] 
[INFO] -----------< io.github.godhuino1-lang:tomograph-report-html >-----------
[INFO] Building Tomograph :: Report :: HTML 0.1.0-SNAPSHOT               [8/11]
[INFO]   from tomograph-report-html/pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] --- resources:3.4.0:resources (default-resources) @ tomograph-report-html ---
[INFO] skip non existing resourceDirectory /home/runner/work/tomograph/tomograph/tomograph-report-html/src/main/resources
[INFO] 
[INFO] --- compiler:3.13.0:compile (default-compile) @ tomograph-report-html ---
[INFO] Recompiling the module because of changed dependency.
[INFO] Compiling 5 source files with javac [debug release 17] to target/classes
[INFO] 
[INFO] --- resources:3.4.0:testResources (default-testResources) @ tomograph-report-html ---
[INFO] skip non existing resourceDirectory /home/runner/work/tomograph/tomograph/tomograph-report-html/src/test/resources
[INFO] 
[INFO] --- compiler:3.13.0:testCompile (default-testCompile) @ tomograph-report-html ---
[INFO] Recompiling the module because of changed dependency.
[INFO] Compiling 3 source files with javac [debug release 17] to target/test-classes
[INFO] 
[INFO] --- surefire:3.5.6:test (default-test) @ tomograph-report-html ---
[INFO] Using auto detected provider org.apache.maven.surefire.junitplatform.JUnitPlatformProvider
[INFO] 
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running io.github.godhuino1.tomograph.report.HtmlReportTest
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.045 s -- in io.github.godhuino1.tomograph.report.HtmlReportTest
[INFO] Running io.github.godhuino1.tomograph.report.OtlpJsonReaderTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.104 s -- in io.github.godhuino1.tomograph.report.OtlpJsonReaderTest
[INFO] Running io.github.godhuino1.tomograph.report.SpanTreeTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.005 s -- in io.github.godhuino1.tomograph.report.SpanTreeTest
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 22, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] 
[INFO] --- jar:3.5.0:jar (default-jar) @ tomograph-report-html ---
[INFO] Building jar: /home/runner/work/tomograph/tomograph/tomograph-report-html/target/tomograph-report-html-0.1.0-SNAPSHOT.jar
[INFO] 
[INFO] ------------< io.github.godhuino1-lang:tomograph-examples >-------------
[INFO] Building Tomograph :: Examples 0.1.0-SNAPSHOT                     [9/11]
[INFO]   from tomograph-examples/pom.xml
[INFO] --------------------------------[ pom ]---------------------------------
[INFO] 
[INFO] --------< io.github.godhuino1-lang:tomograph-example-fakeagent >--------
[INFO] Building Tomograph :: Examples :: Fake Agent 0.1.0-SNAPSHOT      [10/11]
[INFO]   from tomograph-examples/tomograph-example-fakeagent/pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] --- resources:3.4.0:resources (default-resources) @ tomograph-example-fakeagent ---
[INFO] Copying 1 resource from src/main/resources to target/classes
[INFO] 
[INFO] --- compiler:3.13.0:compile (default-compile) @ tomograph-example-fakeagent ---
[INFO] Recompiling the module because of changed dependency.
[INFO] Compiling 4 source files with javac [debug release 17] to target/classes
[INFO] 
[INFO] --- resources:3.4.0:testResources (default-testResources) @ tomograph-example-fakeagent ---
[INFO] skip non existing resourceDirectory /home/runner/work/tomograph/tomograph/tomograph-examples/tomograph-example-fakeagent/src/test/resources
[INFO] 
[INFO] --- compiler:3.13.0:testCompile (default-testCompile) @ tomograph-example-fakeagent ---
[INFO] Recompiling the module because of changed dependency.
[INFO] Compiling 5 source files with javac [debug release 17] to target/test-classes
[INFO] 
[INFO] --- surefire:3.5.6:test (default-test) @ tomograph-example-fakeagent ---
[INFO] Using auto detected provider org.apache.maven.surefire.junitplatform.JUnitPlatformProvider
[INFO] 
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running io.github.godhuino1.tomograph.examples.fakeagent.AgentJarContentsTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.039 s -- in io.github.godhuino1.tomograph.examples.fakeagent.AgentJarContentsTest
[INFO] Running io.github.godhuino1.tomograph.examples.fakeagent.DynamicAttachTest
[attach-test] agent jar resolved to /home/runner/work/tomograph/tomograph/tomograph-javaagent/target/tomograph-agent.jar (exists=true)
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.777 s -- in io.github.godhuino1.tomograph.examples.fakeagent.DynamicAttachTest
[INFO] Running io.github.godhuino1.tomograph.examples.fakeagent.ModuleRewriteTest
[tomograph] module example-fakeagent rewrote io/github/godhuino1/tomograph/examples/fakeagent/FakeChatModel (1784 -> 1879 bytes, loader=bootstrap)
[example-probe] entered chat
[tomograph] module example-fakeagent rewrote io/github/godhuino1/tomograph/examples/fakeagent/FakeChatModel (1784 -> 1879 bytes, loader=bootstrap)
[example-probe] entered chat
[example-probe] entered chat
[tomograph] module example-fakeagent rewrote io/github/godhuino1/tomograph/examples/fakeagent/FakeChatModel (1784 -> 1879 bytes, loader=bootstrap)
[example-probe] entered chat
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.041 s -- in io.github.godhuino1.tomograph.examples.fakeagent.ModuleRewriteTest
[INFO] Running io.github.godhuino1.tomograph.examples.fakeagent.AgentToOtlpEndToEndTest
[e2e] agent jar: /home/runner/work/tomograph/tomograph/tomograph-javaagent/target/tomograph-agent.jar
[e2e] receiver listening on 127.0.0.1:35527
[e2e] received 877 bytes: {"resourceSpans":[{"resource":{"attributes":[{"key":"service.name","value":{"stringValue":"unknown_service:java"}},{"key":"telemetry.sdk.name","value":{"stringValue":"tomograph"}},{"key":"telemetry.sdk.language","value":{"stringValue":"java"}},{"key":"telemetry.sdk.version","value":{"stringValue":"0.1.0-SNAPSHOT"}}]},"scopeSpans":[{"scope":{"name":"io.github.godhuino1.tomograph","version":"0.1.0-SNAPSHOT"},"spans":[{"traceId":"5a010f0cf9aa46bf57af71bb237d9865","spanId":"5a4866788906ae0e","parentSpanId":"6e9922522f82df2c","name":"instrument io/github/godhuino1/tomograph/examples/fakeagent/FakeChatModel","kind":1,"startTimeUnixNano":"1791032412789000000","endTimeUnixNano":"1791032412789000000","attributes":[{"key":"tomograph.module","value":{"stringValue":"example-fakeagent"}},{"key":"tomograph.classfile.bytes","value":{"intValue":"1784"}}],"status":{"code":1}}]}]}]}
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.701 s -- in io.github.godhuino1.tomograph.examples.fakeagent.AgentToOtlpEndToEndTest
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] 
[INFO] --- jar:3.5.0:jar (default-jar) @ tomograph-example-fakeagent ---
[INFO] Building jar: /home/runner/work/tomograph/tomograph/tomograph-examples/tomograph-example-fakeagent/target/tomograph-example-fakeagent-0.1.0-SNAPSHOT.jar
[INFO] 
[INFO] --------< io.github.godhuino1-lang:tomograph-integration-tests >--------
[INFO] Building Tomograph :: Integration Tests 0.1.0-SNAPSHOT           [11/11]
[INFO]   from tomograph-integration-tests/pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] --- resources:3.4.0:resources (default-resources) @ tomograph-integration-tests ---
[INFO] skip non existing resourceDirectory /home/runner/work/tomograph/tomograph/tomograph-integration-tests/src/main/resources
[INFO] 
[INFO] --- compiler:3.13.0:compile (default-compile) @ tomograph-integration-tests ---
[INFO] No sources to compile
[INFO] 
[INFO] --- resources:3.4.0:testResources (default-testResources) @ tomograph-integration-tests ---
[INFO] skip non existing resourceDirectory /home/runner/work/tomograph/tomograph/tomograph-integration-tests/src/test/resources
[INFO] 
[INFO] --- compiler:3.13.0:testCompile (default-testCompile) @ tomograph-integration-tests ---
[INFO] Recompiling the module because of changed dependency.
[INFO] Compiling 1 source file with javac [debug release 17] to target/test-classes
[INFO] -------------------------------------------------------------
[ERROR] COMPILATION ERROR : 
[INFO] -------------------------------------------------------------
[ERROR] /home/runner/work/tomograph/tomograph/tomograph-integration-tests/src/test/java/io/github/godhuino1/tomograph/integration/LangChain4jRealFrameworkTest.java:[83,33] cannot find symbol
  symbol:   variable OPENAI
  location: class dev.langchain4j.model.ModelProvider
[INFO] 1 error
[INFO] -------------------------------------------------------------
[INFO] ------------------------------------------------------------------------
[INFO] Reactor Summary for Tomograph 0.1.0-SNAPSHOT:
[INFO] 
[INFO] Tomograph .......................................... SUCCESS [  0.001 s]
[INFO] Tomograph :: API ................................... SUCCESS [  3.303 s]
[INFO] Tomograph :: Semantic Conventions .................. SUCCESS [  0.616 s]
[INFO] Tomograph :: Exporter :: OTLP ...................... SUCCESS [  1.976 s]
[INFO] Tomograph :: Core .................................. SUCCESS [  2.093 s]
[INFO] Tomograph :: Java Agent ............................ SUCCESS [  0.101 s]
[INFO] Tomograph :: Instrumentation :: LangChain4j ........ SUCCESS [  0.546 s]
[INFO] Tomograph :: Report :: HTML ........................ SUCCESS [  0.689 s]
[INFO] Tomograph :: Examples .............................. SUCCESS [  0.000 s]
[INFO] Tomograph :: Examples :: Fake Agent ................ SUCCESS [  1.997 s]
[INFO] Tomograph :: Integration Tests ..................... FAILURE [  1.031 s]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD FAILURE
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  12.477 s
[INFO] Finished at: 2026-10-03T13:00:14Z
[INFO] ------------------------------------------------------------------------
[ERROR] Failed to execute goal org.apache.maven.plugins:maven-compiler-plugin:3.13.0:testCompile (default-testCompile) on project tomograph-integration-tests: Compilation failure
[ERROR] /home/runner/work/tomograph/tomograph/tomograph-integration-tests/src/test/java/io/github/godhuino1/tomograph/integration/LangChain4jRealFrameworkTest.java:[83,33] cannot find symbol
[ERROR]   symbol:   variable OPENAI
[ERROR]   location: class dev.langchain4j.model.ModelProvider
[ERROR] -> [Help 1]
[ERROR] 
[ERROR] To see the full stack trace of the errors, re-run Maven with the -e switch.
[ERROR] Re-run Maven using the -X switch to enable full debug logging.
[ERROR] 
[ERROR] For more information about the errors and possible solutions, please read the following articles:
[ERROR] [Help 1] http://cwiki.apache.org/confluence/display/MAVEN/MojoFailureException
[ERROR] 
[ERROR] After correcting the problems, you can resume the build with the command
[ERROR]   mvn <args> -rf :tomograph-integration-tests
```

## surefire reports

```
(no surefire report — the failure came earlier, most likely at compile time)
```
