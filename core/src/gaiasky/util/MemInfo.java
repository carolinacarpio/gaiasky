/*
 * This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 * See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.util;

import java.lang.management.ManagementFactory;

/**
 * RAM memory information
 */
public class MemInfo {

    /**
     * The used memory in MiB, which is totalMemory - freeMemory. Given in MB.
     */
    public static double getUsedMemory() {
        return (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) * Constants.BYTE_TO_MB;
    }

    /**
     * The amount of free memory in the JMV. Given in MB.
     */
    public static double getFreeMemory() {
        return (Runtime.getRuntime().freeMemory()) * Constants.BYTE_TO_MB;
    }

    /**
     * The total amount of memory in the JVM. Given in MB.
     */
    public static double getTotalMemory() {
        return (Runtime.getRuntime().totalMemory()) * Constants.BYTE_TO_MB;
    }

    /**
     * The maximum amount of memory the JVM will attempt to use. Same as -Xmx. Given in MB.
     */
    public static double getMaxMemory() {
        return (Runtime.getRuntime().maxMemory()) * Constants.BYTE_TO_MB;
    }

    /**
     * The total amount of RAM memory in the system, in MB.
     */
    public static double getTotalRam() {
        com.sun.management.OperatingSystemMXBean mxbean = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        return mxbean.getTotalPhysicalMemorySize() * Constants.BYTE_TO_MB;
    }

}
