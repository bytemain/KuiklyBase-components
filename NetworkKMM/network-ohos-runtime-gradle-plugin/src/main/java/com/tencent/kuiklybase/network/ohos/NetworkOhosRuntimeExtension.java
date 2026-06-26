package com.tencent.kuiklybase.network.ohos;

import javax.inject.Inject;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

public class NetworkOhosRuntimeExtension {

    private final Property<String> groupId;
    private final Property<String> artifactId;
    private final Property<String> version;
    private final DirectoryProperty outputDir;

    @Inject
    public NetworkOhosRuntimeExtension(ObjectFactory objects) {
        groupId = objects.property(String.class);
        artifactId = objects.property(String.class);
        version = objects.property(String.class);
        outputDir = objects.directoryProperty();
    }

    public Property<String> getGroupId() {
        return groupId;
    }

    public Property<String> getArtifactId() {
        return artifactId;
    }

    public Property<String> getVersion() {
        return version;
    }

    public DirectoryProperty getOutputDir() {
        return outputDir;
    }
}
