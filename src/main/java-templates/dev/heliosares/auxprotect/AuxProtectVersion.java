package dev.heliosares.auxprotect;

/**
 * <a href="https://stackoverflow.com/a/45791972">Credit</a>
 * <p>
 * Don't worry, I also hate this solution. Velocity is stupid and requires the version be stated in an attribute string, so this is the only way for it to be dynamically assigned from the POM.
 */
public class AuxProtectVersion {
    public static final String VERSION = "${project.version}";
}
