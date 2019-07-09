/*
 *  Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.wso2.ballerinalang.compiler;

import org.ballerinalang.compiler.BLangCompilerException;
import org.ballerinalang.compiler.CompilerPhase;
import org.ballerinalang.toml.model.Library;
import org.ballerinalang.toml.model.Manifest;
import org.ballerinalang.toml.parser.ManifestProcessor;
import org.wso2.ballerinalang.compiler.codegen.CodeGenerator;
import org.wso2.ballerinalang.compiler.tree.BLangPackage;
import org.wso2.ballerinalang.compiler.util.CompilerContext;
import org.wso2.ballerinalang.compiler.util.CompilerOptions;
import org.wso2.ballerinalang.compiler.util.ProjectDirConstants;
import org.wso2.ballerinalang.compiler.util.ProjectDirs;

import java.io.IOException;
import java.lang.reflect.Array;
import java.net.URI;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ModuleFileWriter {
    private static final CompilerContext.Key<ModuleFileWriter> MODULE_FILE_WRITER_KEY =
            new CompilerContext.Key<>();

    private final CodeGenerator codeGenerator;
    private final SourceDirectory sourceDirectory;
    private final CompilerPhase compilerPhase;
    private final Manifest manifest;

    public static ModuleFileWriter getInstance(CompilerContext context) {
        ModuleFileWriter moduleFileWriter = context.get(MODULE_FILE_WRITER_KEY);
        if (moduleFileWriter == null) {
            moduleFileWriter = new ModuleFileWriter(context);
        }
        return moduleFileWriter;
    }

    private ModuleFileWriter(CompilerContext context) {
        context.put(MODULE_FILE_WRITER_KEY, this);
        this.codeGenerator = CodeGenerator.getInstance(context);
        this.sourceDirectory = context.get(SourceDirectory.class);
        if (this.sourceDirectory == null) {
            throw new IllegalArgumentException("source directory has not been initialized");
        }
        this.compilerPhase = CompilerOptions.getInstance(context).getCompilerPhase();
        this.manifest = ManifestProcessor.getInstance(context).getManifest();
    }

    public void write(BLangPackage module) {
        // Get the project directory
        Path projectDirectory = this.sourceDirectory.getPath();
        // Check if it is a valid project
        if (!ProjectDirs.isProject(projectDirectory)) {
            // Usually this scenario should be avoided from higher level
            // if this happens we ignore
            return;
        }

        // Ignore unnamed packages
        if (module.packageID.isUnnamed) {
            return;
        }

        // Check if the module is part of the project
        String moduleName = module.packageID.name.value;
        if (!ProjectDirs.isModuleExist(projectDirectory,moduleName)) {
            return;
        }

        // Get the version of the project.
        // Calculate the name of the balo
        // {module}-{lang spec version}-{platform}-{version}.balo
        String baloName = moduleName + ProjectDirConstants.BLANG_COMPILED_PKG_BINARY_EXT;
                          //+ "2019R2" + ProjectDirConstants.FILE_NAME_DELIMITER

        // Get the path to create balo.
        Path baloDir = projectDirectory.resolve(ProjectDirConstants.TARGET_DIR_NAME)
                .resolve(ProjectDirConstants.TARGET_BALO_DIRECTORY);
        // Crate balo directory if it is not there
        if (Files.exists(baloDir)) {
            if (!Files.isDirectory(baloDir)){
                new BLangCompilerException("Found `balo` file instead of a `balo` directory inside target");
            }
        } else {
            try {
                Files.createDirectory(baloDir);
            } catch (IOException e) {
                new BLangCompilerException("Unable to create balo directory inside target");
            }
        }

        // Create the archive over write if exists
        Path baloFile = baloDir.resolve(baloName);
        try(FileSystem balo = createBaloArchive(baloFile)) {
            // Now lets put stuff in
            populateBaloArchive(balo, module);
        } catch (IOException e) {
            // todo Check for permission
            new BLangCompilerException("Failed to create balo");
        }
    }

    private FileSystem createBaloArchive(Path path) throws IOException {
        // Define ZIP File System Properies
        Map<String, String> env = new HashMap<>();
        env.put("create", "true");
        env.put("encoding", "UTF-8");

        /* Locate File on disk for creation */
        URI zip_disk = URI.create("jar:" + path.toUri());
        /* Create ZIP file System */
        return FileSystems.newFileSystem(zip_disk, env);
    }

    private void populateBaloArchive(FileSystem balo, BLangPackage module) throws IOException {
        Path root = balo.getPath("/");
        Path projectDirectory = this.sourceDirectory.getPath();
        Path moduleSourceDir = projectDirectory.resolve(ProjectDirConstants.SOURCE_DIR_NAME)
                .resolve(module.packageID.name.value);
        String moduleName = module.packageID.name.value;
        // Now lets put stuff in according to spec
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

        addMetaData(root);
        addModuleSource(root, moduleSourceDir, moduleName);
        addResources(root, moduleSourceDir);
        addModuleDoc(root, moduleSourceDir);
        addPlatformLibs(root, projectDirectory, moduleName);
    }

    private void addModuleDoc(Path root, Path moduleSourceDir) throws IOException {
        // create the docs directory in zip
        Path moduleMd = moduleSourceDir.resolve(ProjectDirConstants.MODULE_MD_FILE_NAME);
        Path docsDirInBalo = root.resolve(ProjectDirConstants.BALO_DOC_DIR_NAME);
        Path moduleMdInBalo = docsDirInBalo.resolve(ProjectDirConstants.MODULE_MD_FILE_NAME);
        Files.createDirectory(docsDirInBalo);

        if(Files.exists(moduleMd)){
            Files.copy(moduleMd, moduleMdInBalo);
        }
    }

    private void addPlatformLibs(Path root, Path projectDirectory, String moduleName) throws IOException {
        Path platformLibsDir = root.resolve(ProjectDirConstants.BALO_PLATFORM_LIB_DIR_NAME);
        Files.createDirectory(platformLibsDir);

        List<Path> libs = manifest.getPlatform().libraries.stream()
                .filter(lib -> lib.getModules() == null || Arrays.asList(lib.getModules()).contains(moduleName))
                .map(lib -> Paths.get(lib.getPath())).collect(Collectors.toList());

        for (Path lib : libs){
            Path nativeFile = projectDirectory.resolve(lib);
            Path targetPath = platformLibsDir.resolve(lib.getFileName().toString());
            try {
                Files.copy(nativeFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }catch (IOException e){
                e.printStackTrace();
            }
        }
    }

    private void addResources(Path root, Path moduleSourceDir) throws IOException {
        // create the resources directory in zip
        Path resourceDir = moduleSourceDir.resolve(ProjectDirConstants.RESOURCE_DIR_NAME);
        Path resourceDirInBalo = root.resolve(ProjectDirConstants.RESOURCE_DIR_NAME);
        Files.createDirectory(resourceDirInBalo);

        // copy resources file from module directory path in to zip
        PathMatcher filter = FileSystems.getDefault().getPathMatcher("glob:**");
        Files.walkFileTree(resourceDir, new Copy(resourceDir, resourceDirInBalo, filter, filter));
    }

    private void addModuleSource(Path root, Path moduleSourceDir, String moduleName) throws IOException {
        // create the module directory in zip
        Path projectDirectory = this.sourceDirectory.getPath();
        Path srcInBalo = root.resolve(ProjectDirConstants.SOURCE_DIR_NAME);
        Files.createDirectory(srcInBalo);
        Path moduleDirInBalo = srcInBalo.resolve(moduleName);
        Files.createDirectory(moduleDirInBalo);

        // copy only bal file from module directory path in to zip
        PathMatcher fileFilter = FileSystems.getDefault()
                .getPathMatcher("glob:**/*" + ProjectDirConstants.BLANG_SOURCE_EXT);
        // exclude resources and tests directories
        PathMatcher dirFilter = new PathMatcher() {

            @Override
            public boolean matches(Path path) {
                FileSystem fd = FileSystems.getDefault();
                String prefix = moduleDirInBalo
                        .resolve(ProjectDirConstants.RESOURCE_DIR_NAME).toString();

                // Skip resources directory
                if(fd.getPathMatcher("glob:" + prefix + "**").matches(path)){
                    return false;
                }
                // Skip tests directory
                prefix = moduleDirInBalo
                        .resolve(ProjectDirConstants.TEST_DIR_NAME).toString();
                // Skip resources directory
                if(fd.getPathMatcher("glob:" + prefix + "**").matches(path)){
                    return false;
                }
                return true;
            }
        };
        Files.walkFileTree(moduleSourceDir, new Copy(moduleSourceDir, moduleDirInBalo, fileFilter, dirFilter));
    }

    private void addMetaData(Path root) throws IOException {
        Path metaDir = root.resolve(ProjectDirConstants.BALO_METADATA_DIR_NAME);
        Path baloMetaFile = metaDir.resolve(ProjectDirConstants.BALO_METADATA_FILE);
        Path moduleMetaFile = metaDir.resolve(ProjectDirConstants.BALO_MODULE_METADATA_FILE);

        Files.createDirectory(metaDir);
        Files.createFile(baloMetaFile);
        Files.createFile(moduleMetaFile);


    }

    static class Copy extends SimpleFileVisitor<Path> {
        private Path fromPath;
        private Path toPath;
        private StandardCopyOption copyOption;
        private PathMatcher fileFilter;
        private PathMatcher dirFilter;

        public Copy (Path fromPath, Path toPath, PathMatcher file, PathMatcher dir) {
            this.fromPath = fromPath;
            this.toPath = toPath;
            this.copyOption = StandardCopyOption.REPLACE_EXISTING;
            this.fileFilter = file;
            this.dirFilter = dir;
        }

        @Override
        public FileVisitResult preVisitDirectory (Path dir, BasicFileAttributes attrs)
                throws IOException {
            Path targetPath = toPath.resolve(fromPath.relativize(dir).toString());
            if(!dirFilter.matches(targetPath)){
                // we do not visit the sub tree is the directory is filtered out
                return FileVisitResult.SKIP_SUBTREE;
            }
            if (!Files.exists(targetPath)) {
                Files.createDirectory(targetPath);
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile (Path file, BasicFileAttributes attrs)
                throws IOException {
            Path targetPath = toPath.resolve(fromPath.relativize(file).toString());
            if(fileFilter.matches(targetPath)) {
                Files.copy(file, targetPath, copyOption);
            }
            return FileVisitResult.CONTINUE;
        }
    }
}

