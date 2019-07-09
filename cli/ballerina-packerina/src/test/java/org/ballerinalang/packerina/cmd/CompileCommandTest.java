/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.ballerinalang.packerina.cmd;

import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.ballerinalang.compiler.util.ProjectDirConstants;
import picocli.CommandLine;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.stream.Stream;


/**
 * Test cases for ballerina compile command.
 *
 * @since 1.0.0
 */
public class CompileCommandTest extends CommandTest {

    private Path moduleBalo;

    @BeforeClass
    public void setup() throws IOException {
        super.setup();
        Path projectDir = tmpDir.resolve("compileTest");
        Files.createDirectory(projectDir);
        URI uri = null;
        try {
            uri = getClass().getClassLoader().getResource("compile_command_test_project").toURI();
        } catch (URISyntaxException e) {
            new AssertionError("Failed to setup compile test");
        }
        Files.walkFileTree(Paths.get(uri), new Copy(Paths.get(uri), projectDir));
    }

    @Test(description = "Test Compile Command in a Project")
    public void testCompileCommand() throws IOException {
        Path projectDirectory = tmpDir.resolve("compileTest");

        // Compile the project
        String[] compileArgs = {"--skip-tests", "-c", "--jvmTarget"};
        CompileCommand compileCommand = new CompileCommand(projectDirectory, printStream);
        new CommandLine(compileCommand).parse(compileArgs);
        compileCommand.execute();

        // Validate against the spec
        // - target/         <- directory for compile/build output
        // -- bin/           <- Executables will be created here
        // -- balo/          <- .balo files one per built module
        // --- module1.balo  <- balo object of module1
        // --- module2.balo  <- balo object of module2
        // -- apidocs/
        // --- module1/      <- directory containing the HTML files for API docs
        //                      of module1
        // --- module2/
        // -- kubernetes/    <- output of kubernetes compiler extension if used
        // -- potato/        <- output of potato compiler extension
        // -- cache          <- BIR cache directory

        Path target = projectDirectory.resolve(ProjectDirConstants.TARGET_DIR_NAME);
        Assert.assertTrue(Files.exists(target), "Check if target directory is created");

        Assert.assertTrue(Files.exists(target.resolve(ProjectDirConstants.TARGET_BALO_DIRECTORY)),
                "Check if balo directory exists");
        // {module}-{lang spec version}-{platform}-{version}.balo
        String baloName = "mymodule-2019R3-java12-0.1.0.balo";
        this.moduleBalo = target.resolve(ProjectDirConstants.TARGET_BALO_DIRECTORY)
                .resolve("mymodule.balo");
//        Assert.assertTrue(Files.exists(target.resolve(ProjectDirConstants.TARGET_BALO_DIRECTORY)),
//                "Check if balo file exists");

        Path lockFile = projectDirectory.resolve(ProjectDirConstants.LOCK_FILE_NAME);
        Assert.assertTrue(Files.exists(lockFile), "Check if lock file is created");

        readOutput(true);
    }

    @Test(dependsOnMethods = {"testCompileCommand"})
    public void testBaloContents() throws IOException {
        URI baloZip = URI.create("jar:" + moduleBalo.toUri().toString());
        FileSystems.newFileSystem(baloZip, Collections.emptyMap())
                .getRootDirectories()
                .forEach(root -> {
                    try (Stream<Path> stream = Files.list(root)) {
                        // Validate against the spec
                        // /
                        // └─ metadata/
                        //    └─ BALO.toml
                        //    └─ MODULE.toml
                        // └─ src/
                        // └─ resources/
                        // └─ platform-libs/
                        // └─ docs/
                        //    └─ MODULE-DESC.md
                        //    └─ api-docs/
                        Path metadata = root.resolve(ProjectDirConstants.BALO_METADATA_DIR_NAME);
                        Assert.assertTrue(Files.exists(metadata));
                        Assert.assertTrue(Files.isDirectory(metadata));

                        Path baloToml = metadata.resolve(ProjectDirConstants.BALO_METADATA_FILE);
                        Assert.assertTrue(Files.exists(baloToml));

                        Path moduleToml = metadata.resolve(ProjectDirConstants.BALO_MODULE_METADATA_FILE);
                        Assert.assertTrue(Files.exists(moduleToml));

                        // todo validate content of meta files
                        Path srcDir = root.resolve(ProjectDirConstants.SOURCE_DIR_NAME);
                        Assert.assertTrue(Files.exists(srcDir));

                        Path moduleDir = srcDir.resolve("mymodule");
                        Assert.assertTrue(Files.exists(moduleDir));

                        Path mainBal = moduleDir.resolve("main.bal");
                        Assert.assertTrue(Files.exists(mainBal));

                        Path moduleMD = moduleDir.resolve("Module.md");
                        Assert.assertFalse(Files.exists(moduleMD));

                        Path resourceDirInModule = moduleDir.resolve(ProjectDirConstants.RESOURCE_DIR_NAME);
                        Assert.assertFalse(Files.exists(resourceDirInModule));

                        // Check if resources is there
                        Path resourceDir = root.resolve(ProjectDirConstants.RESOURCE_DIR_NAME);
                        Assert.assertTrue(Files.exists(resourceDir));

                        Path resourceDirContent = resourceDir.resolve("resource.txt");
                        Assert.assertTrue(Files.exists(resourceDirContent));

                        // Check for module md
                        Path docsDir = root.resolve(ProjectDirConstants.BALO_DOC_DIR_NAME);
                        Assert.assertTrue(Files.exists(docsDir));

                        Path moduleMdInBalo = docsDir.resolve(ProjectDirConstants.MODULE_MD_FILE_NAME);
                        Assert.assertTrue(Files.exists(moduleMdInBalo));

                        // Check for platform libs
                        Path platformLibDir = root.resolve(ProjectDirConstants.BALO_PLATFORM_LIB_DIR_NAME);
                        Assert.assertTrue(Files.exists(platformLibDir));

                        Path jarFile = platformLibDir.resolve("toml4j.jar");
                        Assert.assertTrue(Files.exists(jarFile));

                    } catch (IOException ex) {
                        throw new AssertionError("Error while reading balo content");
                    }
                });
    }

    @Test(description = "Test Compile Command for a single file.")
    public void testCompileCommandSingleFile() throws IOException {
        Path balFile = tmpDir.resolve("main.bal");
        Files.createFile(balFile);

        String question = "public function main(){ int i =5; }";
        Files.write(balFile, question.getBytes());

        // Compile the project
        String[] compileArgs = {"--skip-tests", "-c", "--jvmTarget"};
        CompileCommand compileCommand = new CompileCommand(tmpDir, printStream);
        new CommandLine(compileCommand).parse(compileArgs);
        compileCommand.execute();

        Assert.assertFalse(Files.exists(tmpDir.resolve(ProjectDirConstants.TARGET_DIR_NAME)),
                "Check if target directory is not created");
        Assert.assertTrue(readOutput().contains("Compile command can be only run inside a Ballerina project"));
        Path lockFile = tmpDir.resolve(ProjectDirConstants.LOCK_FILE_NAME);
        Assert.assertFalse(Files.exists(lockFile), "Check if lock file is created");
    }

    // Check compile command inside a module directory


    static class Copy extends SimpleFileVisitor<Path> {
        private Path fromPath;
        private Path toPath;
        private StandardCopyOption copyOption;


        public Copy(Path fromPath, Path toPath, StandardCopyOption copyOption) {
            this.fromPath = fromPath;
            this.toPath = toPath;
            this.copyOption = copyOption;
        }

        public Copy(Path fromPath, Path toPath) {
            this(fromPath, toPath, StandardCopyOption.REPLACE_EXISTING);
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                throws IOException {

            Path targetPath = toPath.resolve(fromPath.relativize(dir).toString());
            if (!Files.exists(targetPath)) {
                Files.createDirectory(targetPath);
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                throws IOException {

            Files.copy(file, toPath.resolve(fromPath.relativize(file).toString()), copyOption);
            return FileVisitResult.CONTINUE;
        }
    }

    static class TemplateException extends Exception {
        public TemplateException(String message) {
            super(message);
        }
    }

    static class ModuleCreateException extends Exception {
        public ModuleCreateException(String message) {
            super(message);
        }
    }
}
