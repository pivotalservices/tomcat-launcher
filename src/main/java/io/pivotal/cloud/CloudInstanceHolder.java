package io.pivotal.cloud;

import org.springframework.cloud.Cloud;
import org.springframework.cloud.CloudException;
import org.springframework.cloud.CloudFactory;

public class CloudInstanceHolder {
    public static final Object monitor = new Object();
    public static volatile Cloud cloud;

    public static Cloud getCloudInstance() {
        if (null == cloud) {
            synchronized (monitor) {
                if (null == cloud) {
                    try {
                        cloud = new CloudFactory().getCloud();
                    } catch (CloudException e) {
                        //ignore
                        return null;
                    }
                }
            }
        }
        return cloud;
    }
}