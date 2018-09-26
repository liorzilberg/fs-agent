package org.whitesource.agent.dependency.resolver.docker.remotedocker;

import org.whitesource.fs.configuration.RemoteDockerConfiguration;

import java.util.ArrayList;
import java.util.List;

public class RemoteDockersManager {

     private boolean remoteDockersEnabled = false;
     private List<AbstractRemoteDocker> remoteDockersList;

     public RemoteDockersManager(RemoteDockerConfiguration config) {
        remoteDockersList = new ArrayList<>();
        if (config != null) {
            remoteDockersEnabled = config.isRemoteDockerEnabled();
            if (remoteDockersEnabled) {
                if (config.isRemoteDockerAmazonEnabled()) {
                    remoteDockersList.add(new RemoteDockerAmazonECR(config));
                }
            }
        }
    }

    public void pullRemoteDockerImages() {
        if (!remoteDockersEnabled) {
            return;
        }
        for (AbstractRemoteDocker remoteDocker : remoteDockersList) {
            remoteDocker.pullRemoteDockerImages();
        }
    }

    public void removePulledRemoteDockerImages() {
        if (!remoteDockersEnabled) {
            return;
        }
        for (AbstractRemoteDocker remoteDocker : remoteDockersList) {
            remoteDocker.removePulledRemoteDockerImages();
        }
    }
}
