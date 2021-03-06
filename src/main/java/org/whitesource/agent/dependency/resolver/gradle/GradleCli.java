package org.whitesource.agent.dependency.resolver.gradle;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.whitesource.agent.Constants;
import org.whitesource.agent.utils.LoggerFactory;
import org.whitesource.agent.dependency.resolver.DependencyCollector;
import org.whitesource.agent.utils.Cli;
import org.whitesource.agent.utils.CommandLineProcess;

import java.io.IOException;
import java.util.List;

public class GradleCli extends Cli {
    private final Logger logger = LoggerFactory.getLogger(org.whitesource.agent.dependency.resolver.gradle.GradleCli.class);

    protected static final String GRADLE_ASSEMBLE = "assemble";
    protected static final String GRADLE_LOCK = "lock";
    protected static final String GRADLE_PROJECTS = "projects";
    private final String GRADLE_COMMAND = "gradle";
    private final String GRADLE_COMMAND_W_WINDOWS = "gradlew";
    private final String GRADLE_COMMAND_W_LINUX = "./gradlew";

    private String topLevelFolderGradlew = null;

    private String preferredEnvironment;

    public GradleCli(String preferredEnvironment) {
        super();
        this.preferredEnvironment = preferredEnvironment;
    }

    public List<String> runGradleCmd(String rootDirectory, String[] params) {
        try {
            // run gradle dependencies to get dependency tree
            CommandLineProcess commandLineProcess = new CommandLineProcess(rootDirectory, params);
            List<String> lines = commandLineProcess.executeProcess();
            if (commandLineProcess.isErrorInProcess()) {
                // in case gradle is not installed on the local machine, using 'gradlew' command, which uses local gradle wrapper
                setGradleCommandByEnv(params);
                commandLineProcess = new CommandLineProcess(rootDirectory, params);
                lines = commandLineProcess.executeProcess();
                if (!commandLineProcess.isErrorInProcess()) {
                    return lines;
                }
            } else {
                return lines;
            }
        } catch (IOException e) {
            if (StringUtils.isNotBlank(params[0]) && params[0].contains(GRADLE_COMMAND)) {
                setGradleCommandByEnv(params);
                return runGradleCmd(rootDirectory, params);
            } else {
                logger.warn("Error getting results after running Gradle command {} on {}, {}", params, rootDirectory, e.getMessage());
                logger.debug("Error: {}", e.getStackTrace());
            }
        }
        return null;
    }

    // WSE-753 - replace the params' default-environment to the other possibility
    private void setGradleCommandByEnv(String[] params) {
        for (int i = 0; i < params.length; i++) {
            if (params[i].contains(GRADLE_COMMAND)) {
                if (preferredEnvironment.equals(Constants.GRADLE_WRAPPER)) {
                    params[i] = GRADLE_COMMAND;
                } else if (DependencyCollector.isWindows()) {
                    params[i] = GRADLE_COMMAND_W_WINDOWS;
                } else {
                    params[i] = GRADLE_COMMAND_W_LINUX;
                }
                break;
            }
        }
    }

    public String[] getGradleCommandParams(GradleMvnCommand command) {
        String gradleCommand;
        // WSE-753 - use the default gradle environment, set from the config file
        if (preferredEnvironment.equals(Constants.GRADLE_WRAPPER)) {
            if (this.topLevelFolderGradlew != null) {
                gradleCommand = this.topLevelFolderGradlew + Constants.FORWARD_SLASH + GRADLE_COMMAND_W_WINDOWS;
            } else {
                gradleCommand = DependencyCollector.isWindows() ? GRADLE_COMMAND_W_WINDOWS : GRADLE_COMMAND_W_LINUX;
            }
        } else {
            gradleCommand = GRADLE_COMMAND;
        }
        return super.getCommandParams(gradleCommand, command.getCommand());
    }

    public void setTopLevelFolderGradlew(String topLevelFolderGradlew) {
        this.topLevelFolderGradlew = topLevelFolderGradlew;
    }
}


