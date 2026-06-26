package com.tencent.kuiklybase.network.ohos;

import java.util.Locale;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.RelativePath;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.TaskProvider;

public class NetworkOhosRuntimePlugin implements Plugin<Project> {

    private static final String EXTENSION_NAME = "networkOhosRuntime";
    private static final String CONFIGURATION_NAME = "networkOhosRuntimeArtifact";
    private static final String TASK_NAME = "copyNetworkOhosRuntimeLibs";

    @Override
    public void apply(Project project) {
        NetworkOhosRuntimeExtension extension = project.getObjects()
            .newInstance(NetworkOhosRuntimeExtension.class);
        project.getExtensions().add(EXTENSION_NAME, extension);

        extension.getGroupId().convention("com.tencent.kuiklybase");
        extension.getArtifactId().convention("network-ohos-runtime");
        extension.getVersion().convention(project.provider(() -> defaultRuntimeVersion(project)));
        extension.getOutputDir().convention(
            project.getLayout().getProjectDirectory().dir(defaultOutputPath(project))
        );

        Configuration runtimeArtifact = project.getConfigurations().create(CONFIGURATION_NAME, configuration -> {
            configuration.setCanBeConsumed(false);
            configuration.setCanBeResolved(true);
            configuration.setTransitive(false);
            configuration.setDescription("NetworkKMM HarmonyOS native runtime zip.");
            configuration.defaultDependencies(dependencies ->
                dependencies.add(project.getDependencies().create(runtimeDependencyNotation(extension)))
            );
        });

        TaskProvider<Copy> copyTask = project.getTasks().register(TASK_NAME, Copy.class, task -> {
            task.setGroup("kuikly");
            task.setDescription("Copies NetworkKMM HarmonyOS native runtime libraries into the OHOS entry module.");
            task.from(project.provider(() -> project.zipTree(runtimeArtifact.getSingleFile())));
            task.include("arm64-v8a/*.so");
            task.eachFile(details -> details.setRelativePath(new RelativePath(true, details.getName())));
            task.setIncludeEmptyDirs(false);
            task.into(extension.getOutputDir());
            task.doFirst(ignored -> runtimeDependencyNotation(extension));
            task.doLast(ignored -> project.getLogger().lifecycle(
                "Copied NetworkKMM HarmonyOS runtime libraries to {}",
                extension.getOutputDir().get().getAsFile()
            ));
        });

        project.getTasks().register("syncNetworkOhosRuntime", task -> {
            task.setGroup("kuikly");
            task.setDescription("Alias for copyNetworkOhosRuntimeLibs.");
            task.dependsOn(copyTask);
        });
    }

    private static String defaultOutputPath(Project project) {
        if (project.file("ohosApp/entry").exists()) {
            return "ohosApp/entry/libs/arm64-v8a";
        }
        return "entry/libs/arm64-v8a";
    }

    private static String defaultRuntimeVersion(Project project) {
        Object propertyVersion = project.findProperty("networkOhosRuntimeVersion");
        if (isValidVersion(propertyVersion)) {
            return propertyVersion.toString();
        }

        String pluginVersion = NetworkOhosRuntimePlugin.class.getPackage().getImplementationVersion();
        if (isValidVersion(pluginVersion)) {
            return pluginVersion;
        }

        String projectVersion = String.valueOf(project.getVersion());
        if (isValidVersion(projectVersion) && !"unspecified".equals(projectVersion)) {
            return projectVersion;
        }

        return "";
    }

    private static String runtimeDependencyNotation(NetworkOhosRuntimeExtension extension) {
        String groupId = requiredProperty(extension.getGroupId().getOrNull(), "groupId");
        String artifactId = requiredProperty(extension.getArtifactId().getOrNull(), "artifactId");
        String version = requiredProperty(extension.getVersion().getOrNull(), "version");
        return String.format(Locale.US, "%s:%s:%s@zip", groupId, artifactId, version);
    }

    private static String requiredProperty(String value, String name) {
        if (!isValidVersion(value)) {
            throw new GradleException("networkOhosRuntime." + name + " is required.");
        }
        return value;
    }

    private static boolean isValidVersion(Object value) {
        return value != null && !value.toString().trim().isEmpty() && !"null".equals(value.toString());
    }
}
