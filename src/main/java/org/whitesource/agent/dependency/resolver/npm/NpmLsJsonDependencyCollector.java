/**
 * Copyright (C) 2017 WhiteSource Ltd.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.whitesource.agent.dependency.resolver.npm;

import org.eclipse.jgit.util.StringUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.DependencyCollector;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
/**
 * Collect dependencies using 'npm ls' or bower command.
 *
 * @author eugen.horovitz
 */
public class NpmLsJsonDependencyCollector implements DependencyCollector {

    /* --- Statics Members --- */

    private static final Logger logger = LoggerFactory.getLogger(NpmLsJsonDependencyCollector.class);

    public static final String LS_COMMAND = "ls";
    public static final String LS_PARAMETER_JSON = "--json";

    private static final String NPM_COMMAND = isWindows() ? "npm.cmd" : "npm";
    private static final String OS_NAME = "os.name";
    private static final String WINDOWS = "win";
    private static final String DEPENDENCIES = "dependencies";
    private static final String VERSION = "version";
    private static final String lsOnlyProdArgument = "--only=prod";

    /* --- Members --- */

    protected final boolean includeDevDependencies;

    /* --- Constructors --- */

    public NpmLsJsonDependencyCollector(boolean includeDevDependencies) {
        this.includeDevDependencies = includeDevDependencies;
    }

    /* --- Public methods --- */

    @Override
    public Collection<DependencyInfo> collectDependencies(String rootDirectory) {
        Collection<DependencyInfo> dependencies = new LinkedList<>();
        try {
            // execute 'npm ls'
            ProcessBuilder pb = new ProcessBuilder(getLsCommandParams());

            pb.directory(new File(rootDirectory));
            Process process = pb.start();

            // parse 'npm ls' output
            String json = null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                json = reader.lines().reduce("", String::concat);
                reader.close();
            } catch (IOException e) {
                logger.error("error parsing output : {}", e.getMessage());
            }

            if (!StringUtils.isEmptyOrNull(json)) {
                dependencies.addAll(getDependencies(new JSONObject(json)));
            }
        } catch (IOException e) {
            logger.info("Error getting dependencies after running 'npm ls --json' on {}", rootDirectory);
        }

        if (dependencies.isEmpty()) {
            logger.warn("Failed getting dependencies after running 'npm ls --json' Please run 'npm install' on the folder {}", rootDirectory);
        }
        return dependencies;
    }

    /* --- Private methods --- */

    private Collection<DependencyInfo> getDependencies(JSONObject jsonObject) {
        Collection<DependencyInfo> dependencies = new ArrayList<>();
        if (jsonObject.has(DEPENDENCIES)) {
            JSONObject dependenciesJsonObject = jsonObject.getJSONObject(DEPENDENCIES);
            if (dependenciesJsonObject != null) {
                for (String dependencyName : dependenciesJsonObject.keySet()) {
                    JSONObject dependencyJsonObject = dependenciesJsonObject.getJSONObject(dependencyName);
                    if (dependencyJsonObject.keySet().isEmpty()) {
                        logger.debug("Dependency {} has no JSON content", dependencyName);
                    } else {
                        DependencyInfo dependency = getDependency(dependencyName, dependencyJsonObject);
                        dependencies.add(dependency);

                        // collect child dependencies
                        Collection<DependencyInfo> childDependencies = getDependencies(dependencyJsonObject);
                        dependency.getChildren().addAll(childDependencies);
                    }
                }
            }
        }
        return dependencies;
    }

    /* --- Protected methods --- */

    protected String[] getLsCommandParams() {
        if (includeDevDependencies) {
            return new String[]{NPM_COMMAND, LS_COMMAND, LS_PARAMETER_JSON};
        } else {
            return new String[]{NPM_COMMAND, LS_COMMAND, lsOnlyProdArgument, LS_PARAMETER_JSON};
        }
    }

    protected DependencyInfo getDependency(String name, JSONObject jsonObject) {
        String version = jsonObject.getString(VERSION);
        String filename = NpmBomParser.getNpmArtifactId(name, version);

        DependencyInfo dependency = new DependencyInfo();
        dependency.setGroupId(name);
        dependency.setArtifactId(filename);
        dependency.setVersion(version);
        dependency.setFilename(filename);
        dependency.setDependencyType(DependencyType.NPM);
        return dependency;
    }

    /* --- Static methods --- */

    public static boolean isWindows() {
        return System.getProperty(OS_NAME).toLowerCase().contains(WINDOWS);
    }
}